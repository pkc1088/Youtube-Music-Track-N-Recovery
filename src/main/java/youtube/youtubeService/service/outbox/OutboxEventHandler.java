package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import youtube.youtubeService.config.ExecutorConfig.PartitionedExecutor;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.dto.internal.OutboxCreatedEventDto;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.service.QuotaService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventHandler {

    private final OutboxRepository outboxRepository;
    private final OutboxProcessor outboxProcessor;
    private final OutboxStatusUpdater outboxStatusUpdater;
    private final QuotaService quotaService;
    private final PartitionedExecutor partitionedOutboxExecutor;
    private final Map<String, List<CompletableFuture<Void>>> pendingUserTasks = new ConcurrentHashMap<>();


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInitialOutboxEvent(OutboxCreatedEventDto eventDto) {

        Outbox outbox = outboxRepository.findOutboxById(eventDto.outboxId()).orElseThrow(() -> new RuntimeException("Outbox not found, id=" + eventDto.outboxId()));
        handleOutboxEvent(outbox, Outbox.Status.FAILED);
    }

    public void retryFailedOutboxEvents(String userId) {

        List<Outbox> failedOutboxes = outboxRepository.findByUserIdAndStatus(userId, Outbox.Status.FAILED);

        if(failedOutboxes.isEmpty()) {
            log.info("[No failedOutboxes]");
            return;
        }

        // 우선순위 정렬 (ADD 먼저, 그 다음 DELETE, 같은 타입이면 ID 즉 생성순)
        failedOutboxes.sort(Comparator.comparing((Outbox o) -> o.getActionType() == Outbox.ActionType.ADD ? 0 : 1).thenComparing(Outbox::getId));

        log.info("[retryFailedOutboxEvents] Found {} FAILED events. Starting prioritized retry...", failedOutboxes.size());

        for (Outbox retryOutbox : failedOutboxes) {
            long cost = (retryOutbox.getActionType() == Outbox.ActionType.ADD) ? QuotaType.VIDEO_INSERT.getCost() : QuotaType.VIDEO_DELETE.getCost();

            boolean isAffordable = quotaService.checkAndConsumeLua(userId, cost);

            if (isAffordable) {
                log.info("[retryFailedOutboxEvents] Retrying outbox {} (Type: {})", retryOutbox.getId(), retryOutbox.getActionType());
                handleOutboxEvent(retryOutbox, Outbox.Status.DEAD);
            } else {
                log.warn("[retryFailedOutboxEvents] Not enough quota for outbox {}. Marking as DEAD", retryOutbox.getId());
                outboxStatusUpdater.updateOutboxStatus(retryOutbox.getId(), Outbox.Status.DEAD);
            }
        }

        log.info("[retryFailedOutboxEvents] Retry loop done");
    }

    public void handleOutboxEvent(Outbox outbox, Outbox.Status nextStatus) {

        String userKey = outbox.getUserId();
        String playlistId = outbox.getPlaylistId();

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
