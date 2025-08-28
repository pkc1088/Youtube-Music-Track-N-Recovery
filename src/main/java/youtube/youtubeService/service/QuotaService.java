package youtube.youtubeService.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@Service
@RequiredArgsConstructor
public class QuotaService {

    @Transactional(REQUIRES_NEW)
    public boolean consumeQuota(String userId, int amount) {

        return true;
    }
}
