package youtube.youtubeService.service.outbox;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.repository.OutboxRepository;
import java.time.LocalDateTime;
import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@Service
@RequiredArgsConstructor
public class OutboxStatusUpdater {

    private final OutboxRepository outboxRepository;

    @Transactional(REQUIRES_NEW)
    public void updateOutboxStatus(Long outboxId, Outbox.Status status) {

        Outbox outbox = outboxRepository.findById(outboxId).orElseThrow();
        outbox.setStatus(status);

        /*if(status.equals(Outbox.Status.FAILED)) {
            outbox.setRetryCount(outbox.getRetryCount() + 1);
            outbox.setLastAttemptedAt(LocalDateTime.now());
        }*/

        //outboxRepository.save(outbox); // 여기선 id 로 객체 다시 영속 상태로 호출하니 빼도 됨
    }

//    @Transactional(REQUIRES_NEW)
//    public void updateOutboxStatus(Outbox outbox, Outbox.Status status) {
//        outbox.setStatus(status);
//        if(status.equals(Outbox.Status.FAILED)) {
//            outbox.setRetryCount(outbox.getRetryCount() + 1);
//            outbox.setLastAttemptedAt(LocalDateTime.now());
//        }
//        outboxRepository.save(outbox); // 객체 outbox 로 받는 대신 save 없으면 안됨
//    }

}