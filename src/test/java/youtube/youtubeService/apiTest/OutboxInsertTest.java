package youtube.youtubeService.apiTest;

import com.google.api.services.youtube.model.PlaylistItem;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Outbox;
import youtube.youtubeService.service.outbox.OutboxService;

import java.util.*;

@Slf4j
@Service
@Transactional
public class OutboxInsertTest {

    @Autowired
    private OutboxService outboxService;

    public void outboxInsert(List<PlaylistItem> playlistItemList, String accessToken, String userId, String playlistId, List<String> videoIdToInsert) {
        // accessTokenForRecoverUser, userId, playlistId, videoIdToInsert
        for (int i = 0; i < 4; i++) {
            outboxService.outboxInsert(Outbox.ActionType.DELETE, accessToken, userId, playlistId, null, playlistItemList.get(i).getId());
            outboxService.outboxInsert(Outbox.ActionType.ADD, accessToken, userId, playlistId, videoIdToInsert.get(i), null);
        }
    }
}
