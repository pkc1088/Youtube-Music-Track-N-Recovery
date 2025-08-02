package youtube.youtubeService.service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.dto.OutboxCreatedEvent;
import youtube.youtubeService.repository.OutboxRepository;
import java.util.ArrayList;
import java.util.List;

// 구독자, 상태 업데이트
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventHandler {

    private final OutboxRepository outboxRepository;
    private final OutboxProcessor outboxProcessor;
    private final OutboxStatusUpdater outboxStatusUpdater;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(OutboxCreatedEvent event) {

        Outbox outbox = outboxRepository.findById(event.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found"));
        handleOutbox(outbox, Outbox.Status.PENDING, Outbox.Status.FAILED);
        // int operationCount = outboxProcessor.processOutbox(outbox);
    }

    public void retryFailedOutboxEvents() {
        log.info("[retryFailedOutboxEvents] Start retrying FAILED outbox events...");

        List<Outbox> failedOutboxes = outboxRepository.findByStatus(Outbox.Status.FAILED);
        log.info("retryFailedOutboxEvents failedOutboxes Size : {}", failedOutboxes.size());

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
            } else if (operationCount == -1) {// API 완전 실패 → 모든 Row FAILED
                for (Long id : outboxIds) {
                    outboxStatusUpdater.updateOutboxStatus(id, trgStatus);
                    log.info("[OutboxEventHandler] Marked Outbox ID {} as {} (API failed)", id, trgStatus);
                }
            }
            // 0 일 때도 예외 처리해주자.
        }

    }

}

/*
// 구독자, 상태 업데이트
@Slf4j
//@Component
@Service
@RequiredArgsConstructor
public class OutboxEventHandler {

    private final OutboxRepository outboxRepository;
    private final OutboxProcessor outboxProcessor;
    private final OutboxStatusUpdater outboxStatusUpdater;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(OutboxCreatedEvent event) {

        Outbox outbox = outboxRepository.findById(event.getOutboxId()).orElseThrow(() -> new RuntimeException("Outbox not found"));

        int operationCount = outboxProcessor.processOutbox(outbox);

        if (outbox.getActionType() == Outbox.ActionType.ADD) {// ADD 는 단일 row 만 성공
            if(operationCount == 0) {
                outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.SUCCESS);
                log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outbox.getId());
            } else if (operationCount == -1) {
                outboxStatusUpdater.updateOutboxStatus(outbox.getId(), Outbox.Status.FAILED);
                log.info("[OutboxEventHandler] Marked Outbox ID {} as FAILED (API failed)", outbox.getId());
            }
        }
        else if (outbox.getActionType() == Outbox.ActionType.DELETE) { // DELETE 는 Race Condition 문제 때문에 한번에 지움 + 딜레이 줌
            List<Long> outboxIds = outboxRepository.findIdsByUserIdAndPlaylistIdAndVideoIdAndStatusIn(
                    outbox.getUserId(), outbox.getPlaylistId(), outbox.getVideoId(), List.of(Outbox.Status.PENDING));

            if (operationCount > 0) {// DELETE: 실제 제거된 갯수만큼 SUCCESS
                for (int i = 0; i < operationCount; i++) {
                    outboxStatusUpdater.updateOutboxStatus(outboxIds.get(i), Outbox.Status.SUCCESS);
                    log.info("[OutboxEventHandler] Marked Outbox ID {} as SUCCESS", outboxIds.get(i));
                }
                if (operationCount < outboxIds.size()) {// 나머지는 실패 처리
                    for (int i = operationCount; i < outboxIds.size(); i++) {
                        outboxStatusUpdater.updateOutboxStatus(outboxIds.get(i), Outbox.Status.FAILED);
                        log.info("[OutboxEventHandler] Marked Outbox ID {} as FAILED", outboxIds.get(i));
                    }
                }
            } else if (operationCount == -1) {// API 완전 실패 → 모든 Row FAILED
                for (Long id : outboxIds) {
                    outboxStatusUpdater.updateOutboxStatus(id, Outbox.Status.FAILED);
                    log.info("[OutboxEventHandler] Marked Outbox ID {} as FAILED (API failed)", id);
                }
            }
            // 0 일 때도 예외 처리해주자.
        }

    }

}*/
