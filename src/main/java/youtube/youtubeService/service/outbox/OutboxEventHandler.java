package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import youtube.youtubeService.config.ExecutorConfig.PartitionedExecutor;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.dto.OutboxCreatedEventDto;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.service.QuotaService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventHandler {

    private final OutboxRepository outboxRepository;
    private final OutboxProcessor outboxProcessor;
    private final OutboxStatusUpdater outboxStatusUpdater;
    private final QuotaService quotaService;
    private final Map<String, List<CompletableFuture<Void>>> pendingUserTasks = new ConcurrentHashMap<>();
    private final PartitionedExecutor partitionedOutboxExecutor; // (수정) 단일 Executor 대신 PartitionedExecutor 를 주입받음

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInitialOutboxEvent(OutboxCreatedEventDto eventDto) {

        Outbox outbox = outboxRepository.findById(eventDto.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found, id=" + eventDto.getOutboxId()));
        handleOutboxEvent(outbox, Outbox.Status.FAILED);

    }

    public void retryFailedOutboxEvents(String userId) {

        List<Outbox> failedOutboxes = outboxRepository.findByUserIdAndStatus(userId, Outbox.Status.FAILED);

        int failedOutboxesSize = failedOutboxes.size();

        if(failedOutboxesSize == 0) {
            log.info("[No failedOutboxes]");
            return;
        }

        boolean affordToRetryAllAtOnce = quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_DELETE.getCost() * failedOutboxesSize);

        if(!affordToRetryAllAtOnce) {
            log.info("[Not Affordable Quota To Retry Failed Outboxes -> Make Them All DEAD]");
            failedOutboxes.forEach(outbox -> outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.DEAD));
            return;
        }

        log.info("[retryFailedOutboxEvents] Start retrying {} FAILED outbox events...", failedOutboxesSize);

        for (Outbox retryOutbox : failedOutboxes) {
            handleOutboxEvent(retryOutbox, Outbox.Status.DEAD);
        }

        log.info("[retryFailedOutboxEvents] Retry loop done");
    }

    public void handleOutboxEvent(Outbox outbox, Outbox.Status nextStatus) {

        String userKey = outbox.getUserId();
        String playlistId = outbox.getPlaylistId(); // (중요) DTO 에 userKey 뿐만 아니라 playlistId (파티션 키)도 포함되어야 함

        if (userKey == null || playlistId == null) {
            log.warn("::: [No userKey or playlistId in the Outbox]");
            return;
        }

        // (수정) CompletableFuture.runAsync 대신(내부적으로 쓰긴함) partitionedExecutor.submit 사용
        CompletableFuture<Void> future = partitionedOutboxExecutor.submit(
            playlistId, // 파티션 키 전달
            () -> {     // 실행할 작업 (Runnable)
                String threadName = Thread.currentThread().getName();
                log.info("::: START [{}] [ID-{}] [P-Key-{}]", threadName, outbox.getId(), playlistId);

                try {
                    boolean apiOperatedCheck = outboxProcessor.processOutbox(outbox);
                    outboxStatusUpdater.updateOutboxStatus(outbox.getId(), apiOperatedCheck ? Outbox.Status.SUCCESS : nextStatus);
                    log.info("::: Marked Outbox ID {} as {}", outbox.getId(), apiOperatedCheck ? Outbox.Status.SUCCESS : nextStatus);

                } catch (Exception e) {
                    outboxStatusUpdater.updateOutboxStatus(outbox.getId(), nextStatus);
                    log.warn("::: [Exception] [ID-{}] [P-key-{}]: {}", outbox.getId(), playlistId, e.getMessage());
                    log.info("::: Marked Outbox ID {} as {}", outbox.getId(), nextStatus);
                }

                log.info("::: END [{}] [ID-{}] [P-Key-{}]", threadName, outbox.getId(), playlistId);
            }
        );

        List<CompletableFuture<Void>> userTasks = pendingUserTasks.computeIfAbsent(userKey, k -> new CopyOnWriteArrayList<>());

        userTasks.add(future);

        future.whenComplete((result, exception) -> {
            log.info("::: Outbox Task[ID-{}] 완료, 리스트[Key-{}]에서 제거", outbox.getId(), userKey);
            userTasks.remove(future);
        });
    }

    public void waitForPendingOutboxEvents(String userKey) {
        // 1. 맵에서 현재 작업 리스트를 가져옴 (ConcurrentHashMap 이 보장)
        List<CompletableFuture<Void>> currentTasks = pendingUserTasks.get(userKey);
        if (currentTasks == null || currentTasks.isEmpty()) {
            log.info("[Key-{}] 기다릴 작업이 없습니다.", userKey);
            return;
        }
        // 2. 리스트의 "현재 스냅샷"을 복사. currentTasks 가 CopyOnWriteArrayList 이므로, 이 스냅샷 생성(new ArrayList<>) 중에 'whenComplete' 스레드가 'remove' 를 해도 충돌 X
        List<CompletableFuture<Void>> tasksToWaitFor = new ArrayList<>(currentTasks);
        if (tasksToWaitFor.isEmpty()) { // 스냅샷을 만드는 그 짧은 순간에 모든 작업이 완료되어 remove 됐을 경우
            log.info("[Key-{}] (스냅샷) 기다릴 작업이 없습니다.", userKey);
            return;
        }

        log.info("[Key-{}] {}개의 보류 중인 작업 완료를 기다립니다...", userKey, tasksToWaitFor.size());
        try {
            // 복사본(스냅샷)에 대해서만 join()을 수행
            CompletableFuture.allOf(tasksToWaitFor.toArray(new CompletableFuture[0])).join();
            log.info("[Key-{}] 스냅샷의 모든 작업 완료됨.", userKey);
            // 이 메서드는 리스트나 맵에서 아무것도 'clear' 하지 X. 각 Future 가 whenComplete 콜백을 통해 스스로를 리스트에서 제거하기 때문
        } catch (Exception e) {
            log.error("[Key-{}] 작업 대기 중 예외 발생: {}", userKey, e.getMessage(), e);
        }

    }

    public void cleanUpUser(String userId) {
        List<CompletableFuture<Void>> task = pendingUserTasks.remove(userId);
        if (task != null) {
            log.info("Cleaned up [user-{}, task-{}] from the Map", userId, task.size());
        }
    }

    public void cleanUpAllUsers() {
        int totalCleaned = pendingUserTasks.size();
        pendingUserTasks.clear();
        log.info("Cleaned up all {} users from pending tasks", totalCleaned);
    }
}


/** CURRENT TEMP CODE THAT WORKED 1105 <- 플리단위 비동기

    private final Map<String, List<CompletableFuture<Void>>> pendingUserTasks = new ConcurrentHashMap<>();
    private final Executor outboxExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
     public CompletableFuture<Void> handleOutboxEvent(OutboxCreatedEventDto event) {

         String userKey = event.getUserId();
         if (userKey == null) { // userKey가 없는 경우, 별도 처리하거나(예: "global" 키 사용) 무시합니다.
             log.warn("No user key in this outbox ID: {}", event.getOutboxId());
         }

         CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
             String threadName = Thread.currentThread().getName();
             log.info("::::::Thread Name(handleOutboxEvent - start) : {}", threadName);

             Outbox outbox = outboxRepository.findById(event.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found, id=" + event.getOutboxId()));

             try {
                 boolean apiOperatedCheck = outboxProcessor.processOutbox(outbox);
                 outboxStatusUpdater.updateOutboxStatus(outbox.getId(), apiOperatedCheck ? Outbox.Status.SUCCESS : Outbox.Status.FAILED);
                 log.info("::::::Thread Name(handleOutboxEvent - end) : {} [ID-{}]", threadName, outbox.getId());

             } catch (Exception e) {
                 outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.FAILED);
                 log.warn("[OutboxEventHandler] Exception occurred for ID {}: {}", outbox.getId(), e.getMessage());
             }
         }, outboxExecutor);

         List<CompletableFuture<Void>> userTasks = pendingUserTasks.computeIfAbsent(userKey, k -> new CopyOnWriteArrayList<>());

         userTasks.add(future);

         future.whenComplete((result, exception) -> {
             log.info("Task 완료, 리스트에서 제거. Key: {}", userKey);
             userTasks.remove(future);
         });

         return future;
     }


     public void waitForPendingOutboxEvents(String userKey) {
         // 1. 맵에서 현재 작업 리스트를 가져옴 (ConcurrentHashMap이 보장)
         List<CompletableFuture<Void>> currentTasks = pendingUserTasks.get(userKey);
         if (currentTasks == null || currentTasks.isEmpty()) {
             log.info("[Key-{}] 기다릴 작업이 없습니다.", userKey);
             return;
         }
         // 2. (핵심) 리스트의 "현재 스냅샷"을 복사.
         // currentTasks 가 CopyOnWriteArrayList 이므로, 이 스냅샷 생성(new ArrayList<>) 중에 'whenComplete' 스레드가 'remove' 를 해도 충돌하지 않습니다.
         List<CompletableFuture<Void>> tasksToWaitFor = new ArrayList<>(currentTasks);
         if (tasksToWaitFor.isEmpty()) { // (스냅샷을 만드는 그 짧은 순간에 모든 작업이 완료되어 remove 됐을 경우)
             log.info("[Key-{}] (스냅샷) 기다릴 작업이 없습니다.", userKey);
             return;
         }

         log.info("[Key-{}] {}개의 보류 중인 작업 완료를 기다립니다...", userKey, tasksToWaitFor.size());
         try {
             // 복사본(스냅샷)에 대해서만 join()을 수행합니다.
             CompletableFuture.allOf(tasksToWaitFor.toArray(new CompletableFuture[0])).join();
             log.info("[Key-{}] 스냅샷의 모든 작업 완료됨.", userKey);
             // 참고: 이 메서드는 리스트나 맵에서 아무것도 'clear'하지 않습니다.
             // 각 Future가 whenComplete 콜백을 통해 스스로를 리스트에서 제거하기 때문입니다.
         } catch (Exception e) {
            log.error("[Key-{}] 작업 대기 중 예외 발생: {}", userKey, e.getMessage(), e);
         }
     }

     public void retryFailedOutboxEvents(String userId) {
         log.info("[retryFailedOutboxEvents] Start retrying FAILED outbox events...");

         List<Outbox> failedOutboxes = outboxRepository.findByUserIdAndStatus(userId, Outbox.Status.FAILED);
         int failedOutboxesSize = failedOutboxes.size();
         log.info("[retryFailedOutboxEvents failedOutboxes Size] : {}", failedOutboxesSize);

         if(failedOutboxesSize == 0) return;

         boolean affordToRetryAllAtOnce = quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_DELETE.getCost() * failedOutboxesSize);

         if(!affordToRetryAllAtOnce) {
             log.info("[Not Affordable Quota To Retry Failed Outboxes -> Make Them All DEAD]");
             failedOutboxes.forEach(outbox -> outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.DEAD));
             return;
        }

         for (Outbox outbox : failedOutboxes) {
         try {
             log.info("[retryFailedOutboxEvents] Retrying Outbox ID: {}", outbox.getId());
             handleOutbox(outbox, Outbox.Status.DEAD);
         } catch (Exception e){
             log.warn("[retryFailedOutboxEvents] Exception on Outbox ID {} -> {}", outbox.getId(), e.getMessage());
             outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.DEAD);
         }
         }

         log.info("[retryFailedOutboxEvents] Retry loop done");
     }

     public void handleOutbox(Outbox outbox, Outbox.Status trgStatus) {
         boolean apiOperatedCheck = outboxProcessor.processOutbox(outbox);

         if(apiOperatedCheck) {
             outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.SUCCESS);
             log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outbox.getId());
         } else {
             outboxStatusUpdater.updateOutboxStatus(outbox.getId(), trgStatus);
             log.info("[OutboxEventHandler] Marked Outbox ID {} as {} (API failed)", outbox.getId(), trgStatus);
         }
     }
*/


/** OGCODE BEOFRE 1104
 @Slf4j
 @Service
 @RequiredArgsConstructor
 public class OutboxEventHandler {

 private final OutboxRepository outboxRepository;
 private final OutboxProcessor outboxProcessor;
 private final OutboxStatusUpdater outboxStatusUpdater;
 private final QuotaService quotaService;

 //    @Async("outboxExecutor")
 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 public void handleOutboxEvent(OutboxCreatedEventDto event) {
 //        log.info("::::::Thread Name(handleOutboxEvent - start) : " + Thread.currentThread().getName());
 Outbox outbox = outboxRepository.findById(event.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found"));
 handleOutbox(outbox, Outbox.Status.FAILED);
 //        log.info("::::::Thread Name(handleOutboxEvent - end): " + Thread.currentThread().getName());
 }

 public void retryFailedOutboxEvents(String userId) {
 log.info("[retryFailedOutboxEvents] Start retrying FAILED outbox events...");

 List<Outbox> failedOutboxes = outboxRepository.findByUserIdAndStatus(userId, Outbox.Status.FAILED);
 int failedOutboxesSize = failedOutboxes.size();
 log.info("[retryFailedOutboxEvents failedOutboxes Size] : {}", failedOutboxesSize);

 if(failedOutboxesSize == 0) return;

 boolean affordToRetryAllAtOnce = quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_DELETE.getCost() * failedOutboxesSize);


        if(!affordToRetryAllAtOnce) {
                log.info("[Not Affordable Quota To Retry Failed Outboxes -> Make Them All DEAD]");
                failedOutboxes.forEach(outbox -> outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.DEAD));
                return;
                }

                for (Outbox outbox : failedOutboxes) {
                log.info("[retryFailedOutboxEvents] Retrying Outbox ID: {}", outbox.getId());
                handleOutbox(outbox, Outbox.Status.DEAD);
                }

                log.info("[retryFailedOutboxEvents] Retry loop done");
                }

public void handleOutbox(Outbox outbox, Outbox.Status trgStatus) {
//        log.info("::::::Thread Name(handleOutbox - start) : " + Thread.currentThread().getName());
        boolean apiOperatedCheck = outboxProcessor.processOutbox(outbox);
//        long startTime = System.nanoTime();  // 트랜잭션 시간 측정 시작

        if(apiOperatedCheck) {
        outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.SUCCESS);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outbox.getId());
        } else {
        outboxStatusUpdater.updateOutboxStatus(outbox.getId(), trgStatus);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as {} (API failed)", outbox.getId(), trgStatus);
        }
        }

        }


*/


/** OGCODE BEFORE 1024
 @Slf4j
 @Service
 @RequiredArgsConstructor
 public class OutboxEventHandler {

 private final OutboxRepository outboxRepository;
 private final OutboxProcessor outboxProcessor;
 private final OutboxStatusUpdater outboxStatusUpdater;
 private final QuotaService quotaService;

 @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 public void handleOutboxEvent(OutboxCreatedEventDto event) {

 Outbox outbox = outboxRepository.findById(event.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found"));
 handleOutbox(outbox, Outbox.Status.PENDING, Outbox.Status.FAILED);
 // int operationCount = outboxProcessor.processOutbox(outbox);
 }

 public void retryFailedOutboxEvents(String userId) {
 log.info("[retryFailedOutboxEvents] Start retrying FAILED outbox events...");

 List<Outbox> failedOutboxes = outboxRepository.findByUserIdAndStatus(userId, Outbox.Status.FAILED);
 int failedOutboxesSize = failedOutboxes.size();
 log.info("[retryFailedOutboxEvents failedOutboxes Size] : {}", failedOutboxesSize);

 if(failedOutboxesSize == 0) return; // added

 boolean affordToRetryAllAtOnce = quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_DELETE.getCost() * failedOutboxesSize);


        if(!affordToRetryAllAtOnce) {
                log.info("[Not Affordable Quota To Retry Failed Outboxes -> Make Them All DEAD]");
                failedOutboxes.forEach(outbox -> outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.DEAD));
                return;
                }

                for (Outbox outbox : failedOutboxes) {
                log.info("[retryFailedOutboxEvents] Retrying Outbox ID: {}", outbox.getId());
                handleOutbox(outbox, Outbox.Status.FAILED, Outbox.Status.DEAD);
                }

                log.info("[retryFailedOutboxEvents] Retry loop done");
                }

public void handleOutbox(Outbox outbox, Outbox.Status srcStatus, Outbox.Status trgStatus) {

        if (outbox.getActionType() == Outbox.ActionType.ADD) {// ADD 는 단일 row 만 성공

        int operationCount = outboxProcessor.processOutbox(outbox);

        if(operationCount == 0) {
        outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.SUCCESS);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outbox.getId());
        } else if (operationCount == -1) {
        outboxStatusUpdater.updateOutboxStatus(outbox.getId(), trgStatus);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as {} (API failed)", outbox.getId(), trgStatus);
        }
        }

        else if (outbox.getActionType() == Outbox.ActionType.DELETE) { // DELETE 는 Race Condition 문제 때문에 한번에 지움 + 딜레이 줌

        List<Long> outboxIds = outboxRepository.findIdsByUserIdAndPlaylistIdAndVideoIdAndStatusIn(
        outbox.getUserId(), outbox.getPlaylistId(), outbox.getVideoId(), List.of(srcStatus));

        if(outboxIds.isEmpty()) return;

        int operationCount = outboxProcessor.processOutbox(outbox);

        if (operationCount > 0) {// DELETE: 실제 제거된 갯수만큼 SUCCESS
        for (int i = 0; i < operationCount; i++) {
        outboxStatusUpdater.updateOutboxStatus(outboxIds.get(i), Outbox.Status.SUCCESS);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outboxIds.get(i));
        }
        if (operationCount < outboxIds.size()) {// 나머지는 실패 처리
        for (int i = operationCount; i < outboxIds.size(); i++) {
        outboxStatusUpdater.updateOutboxStatus(outboxIds.get(i), trgStatus);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as {}", outboxIds.get(i), trgStatus);
        }
        }
        } else if (operationCount == -1 || operationCount == 0) {// API 완전 실패 → 모든 Row FAILED/DEAD
        for (Long id : outboxIds) {
        outboxStatusUpdater.updateOutboxStatus(id, trgStatus);
        log.info("[OutboxEventHandler] Marked Outbox ID {} as {} (API failed)", id, trgStatus);
        }
        }
        }

        }

        }

 */