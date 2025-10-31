package youtube.youtubeService.service.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.OutboxCreatedEventDto;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoverOrchestrationService {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final YoutubeService youtubeService;
    private final OutboxEventHandler outboxEventHandler;

    public void allPlaylistsRecoveryOfAllUsers() {
        // 0. 전체 유저 목록에서 순차적으로 유저를 뽑기
        List<Users> users = userService.findAllUsers();

        for (Users user : users) {
            String userId = user.getUserId();
            String countryCode = user.getCountryCode();
            log.info("userId : {}", userId);

            // 1. 유저 아이디로 accessToken 발급
            String accessToken = userService.getNewAccessTokenByUserId(userId);
            if(accessToken.equals("")) {
                // 예외 터지면 getNewAccessToken 에서 고객은 제거 했을꺼고, 다음 고객으로 넘기는 시나리오
                log.info("abort scheduling bc user left");
                continue;
            }

            // 2. 유저 아이디로 조회한 모든 플레이리스트 & 음악을 디비에서 뽑아서 복구 시스템 가동
            List<Playlists> playListsSet = playlistService.findAllPlaylistsByUserId(userId);
            for (Playlists playlist : playListsSet) {
                log.info("{} start", playlist.getPlaylistTitle());
                try {
//                    log.info("::::::Thread Name(allPlaylistsRecoveryOfAllUsers - start) : " + Thread.currentThread().getName());
//                    long overallStart = System.nanoTime();
                    youtubeService.fileTrackAndRecover(userId, playlist, countryCode, accessToken);
//                    long overallEnd = System.nanoTime(); // 측정 종료
//                    long elapsedMs = (overallEnd - overallStart) / 1_000_000;
//                    log.info("[RecoverOrchestrationService transaction time] {} ms", elapsedMs);
//                    log.info("::::::Thread Name(allPlaylistsRecoveryOfAllUsers - end) : " + Thread.currentThread().getName());
                } catch (QuotaExceededException ex) {
                    log.warn("[Quota Exceed EX at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), ex.getMessage());
                } catch (Exception e) {// 예상 못한 런타임 에러 방어
                    log.warn("[Unexpected Error at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), e.getMessage());
                }
            }
            // 유저 단위 retry 수행 전에 모든 AFTER_COMMIT Outbox가 완료되었는지 보장
            // 이 메서드가 내부적으로 CompletableFuture join 처리
            // outboxEventHandler.waitForPendingOutboxEvents(userId);

            outboxEventHandler.retryFailedOutboxEvents(userId);
        }

    }

    public void allPlaylistsRecoveryOfOneParticularUserTest() {

        String userId  = "112735690496635663877";
        String accessToken = userService.getNewAccessTokenByUserId(userId);
        String countryCode = "KR";

        if(accessToken.equals("")) {
            log.info("abort scheduling bc user left");
            return;
        }

        List<Playlists> playListsSet = playlistService.findAllPlaylistsByUserId(userId);
        for (Playlists playlist : playListsSet) {
            log.info("{} start", playlist.getPlaylistTitle());
            try {
                youtubeService.fileTrackAndRecover(userId, playlist, countryCode, accessToken);
            } catch (QuotaExceededException ex) {
                log.warn("[Quota Exceed EX at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), ex.getMessage());
            } catch (Exception e) {// 예상 못한 런타임 에러 방어
                log.warn("[Unexpected Error at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), e.getMessage());
            }
        }
        // 고객별 마지막에 FAIL 난 얘들 한번 싹 재시도
        outboxEventHandler.retryFailedOutboxEvents(userId);
    }
}
