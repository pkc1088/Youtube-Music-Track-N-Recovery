package youtube.youtubeService.service.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoverOrchestrationService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PlaylistService playlistService;
    private final YoutubeService youtubeService;
    private final OutboxEventHandler outboxEventHandler;

    public void allPlaylistsRecoveryOfAllUsers() {
        // 0. 전체 유저 목록에서 순차적으로 유저를 뽑기
        List<Users> users = userRepository.findAllUsers();

        for (Users user : users) {
            String userId = user.getUserId(); // String userId  = "112735690496635663877";
            log.info("userId : {}", userId);
            // 1. 유저 아이디로 accessToken 발급
            String accessToken = userService.getNewAccessTokenByUserId(userId);
            if(accessToken.equals("")) {
                log.info("abort scheduling bc user left");
                continue;
            }// 예외 터지면 getNewAccessToken 에서 고객은 제거 했을꺼고, 다음 고객으로 넘기는 시나리오

            // 2. 유저 아이디로 조회한 모든 플레이리스트 & 음악을 디비에서 뽑아서 복구 시스템 가동
            List<Playlists> playListsSet = playlistService.getPlaylistsByUserId(userId);
            for (Playlists playlist : playListsSet) {
                log.info("{} start", playlist.getPlaylistTitle());
                try {
                    youtubeService.fileTrackAndRecover(userId, playlist.getPlaylistId(), accessToken);
                } catch (IOException e) {// playlist 자체가 제거된 경우 예외처리 필요
                    playlistService.removePlaylistsFromDB(userId, Collections.singletonList(e.getMessage()));
                    log.info("remove the playlist({}) from DB", e.getMessage());
                    log.info("scheduler caught and then move to next playlist");
                } catch (Exception e) {// 예상 못한 런타임 에러 방어
                    log.warn("unexpected error for playlist {}, skip to next. {}", playlist.getPlaylistId(), e.getMessage());
                    e.printStackTrace();
                }
            }
            outboxEventHandler.retryFailedOutboxEvents();
        }

        log.info("auto scheduler done");
    }

    public void allPlaylistsRecoveryOfOneParticularUserTest() {

        String userId  = "112735690496635663877";
        String accessToken = userService.getNewAccessTokenByUserId(userId);
        if(accessToken.equals("")) {
            log.info("abort scheduling bc user left");
            return;
        }

        List<Playlists> playListsSet = playlistService.getPlaylistsByUserId(userId);
        for (Playlists playlist : playListsSet) {
            log.info("{} start", playlist.getPlaylistTitle());
            try {
                youtubeService.fileTrackAndRecover(userId, playlist.getPlaylistId(), accessToken);
            } catch (IOException e) {// playlist 자체가 제거된 경우 예외처리 필요
                playlistService.removePlaylistsFromDB(userId, Collections.singletonList(e.getMessage()));
                log.info("remove the playlist({}) from DB", e.getMessage());
                log.info("scheduler caught and then move to next playlist");
            } catch (Exception e) {// 예상 못한 런타임 에러 방어
                log.warn("unexpected error for playlist {}, skip to next. {}", playlist.getPlaylistId(), e.getMessage());
                e.printStackTrace();
            }
        }

        // 고객별 마지막에 FAIL 난 얘들 한번 싹 재시도
        outboxEventHandler.retryFailedOutboxEvents();
    }
}
