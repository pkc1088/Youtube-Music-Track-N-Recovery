package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.dto.OutboxCreatedEventDto;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.repository.OutboxRepository;
import youtube.youtubeService.service.QuotaService;
import java.util.List;

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
        log.info("::::::Thread Name(handleOutboxEvent - start) : " + Thread.currentThread().getName());
        Outbox outbox = outboxRepository.findById(event.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found"));
        handleOutbox(outbox, Outbox.Status.FAILED);
        log.info("::::::Thread Name(handleOutboxEvent - end): " + Thread.currentThread().getName());
    }

    public void retryFailedOutboxEvents(String userId) {
        log.info("[retryFailedOutboxEvents] Start retrying FAILED outbox events...");

        List<Outbox> failedOutboxes = outboxRepository.findByUserIdAndStatus(userId, Outbox.Status.FAILED);
        int failedOutboxesSize = failedOutboxes.size();
        log.info("[retryFailedOutboxEvents failedOutboxes Size] : {}", failedOutboxesSize);

        if(failedOutboxesSize == 0) return;

        boolean affordToRetryAllAtOnce = quotaService.checkAndConsumeLua(userId, QuotaType.VIDEO_DELETE.getCost() * failedOutboxesSize);

        /**
         * 여기서 재시도 할때 할당량 초과 체크하면 됨, DELETE 는 벌크니까(<-- 이젠 아님) Retry 전체로 따져서 한번에 처리 못하면 그냥 다 DEAD 만들자
         * */
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
        log.info("::::::Thread Name(handleOutbox - start) : " + Thread.currentThread().getName());

        boolean apiOperatedCheck = outboxProcessor.processOutbox(outbox);

        if(apiOperatedCheck) {
            outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.SUCCESS);
            log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outbox.getId());
        } else {
            outboxStatusUpdater.updateOutboxStatus(outbox.getId(), trgStatus);
            log.info("[OutboxEventHandler] Marked Outbox ID {} as {} (API failed)", outbox.getId(), trgStatus);
        }
        log.info("::::::Thread Name(handleOutbox - end) : " + Thread.currentThread().getName());
    }

}

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