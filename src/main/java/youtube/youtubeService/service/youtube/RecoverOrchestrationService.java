package youtube.youtubeService.service.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoverOrchestrationService {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final MusicService musicService;
    private final YoutubeService youtubeService;
    private final OutboxEventHandler outboxEventHandler;
    @Qualifier("userExecutor") private final Executor userExecutor;

    public void allPlaylistsRecoveryOfAllUsers() {

        List<Users> users = userService.findAllUsers();
        //List<Users> usersWithPlaylists = userService.findAllUsersWithPlaylists();

        List<CompletableFuture<Void>> allUserFutures = new ArrayList<>();

        for (Users user : users) {

            CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                try {
                    String userId = user.getUserId();
                    String countryCode = user.getCountryCode();
                    String refreshToken = user.getRefreshToken();
                    String accessToken = userService.getNewAccessTokenByUserId(userId, refreshToken);
                    log.info("[userId: {}]", userId);

                    if (accessToken.equals("")) {
                        log.info("abort scheduling bc user left");
                        return; // continue
                    }

                    List<Playlists> playListsSet = playlistService.findAllPlaylistsByUserId(userId);
                    // 1-1 해당 유저의 모든 Music을 "단 한 번의 쿼리"로 가져옴
                    List<Music> allMusicForUser = musicService.findAllWithPlaylistByPlaylistIn(playListsSet);
                    // 1-2 Playlist ID별로 그룹화 (DB 부하 감소)
                    Map<String, List<Music>> musicMapByPlaylistId = allMusicForUser.stream().collect(
                            Collectors.groupingBy(music -> music.getPlaylist().getPlaylistId())
                    );

                    for (Playlists playlist : playListsSet) {
                        log.info("[playlistTitle: {}]", playlist.getPlaylistTitle());
                        try {
                            // 1-3 미리 조회한 Music 목록을 파라미터로 전달
                            List<Music> musicForThisPlaylist = musicMapByPlaylistId.getOrDefault(playlist.getPlaylistId(), Collections.emptyList());
                            youtubeService.fileTrackAndRecover(userId, playlist, countryCode, accessToken, musicForThisPlaylist);
                        } catch (QuotaExceededException ex) {
                            log.warn("[Quota Exceed EX at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), ex.getMessage());
                        } catch (Exception e) {
                            log.warn("[Unexpected Error at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), e.getMessage());
                        }
                    }

                    // ----------- FIRST -----------
                    log.info("[Waiting for all Outbox tasks to finish for user {}]", userId);
                    outboxEventHandler.waitForPendingOutboxEvents(userId);
                    log.info("[Waiting done for user {}]", userId);

                    // ----------- RETRY -----------
                    outboxEventHandler.retryFailedOutboxEvents(userId);
                    log.info("[{}] Waiting for retry batch...", userId);
                    outboxEventHandler.waitForPendingOutboxEvents(userId);
                    log.info("[{}] Retry batch complete.", userId);

                } catch (Exception e) {
                    log.error("[{}] Unrecoverable error in user processing thread: {}", user.getUserId(), e.getMessage(), e);
                } finally {
                    // 유저 처리 완료 후 무조건 cleanup
                    outboxEventHandler.cleanUpUser(user.getUserId());
                }
            }, userExecutor);

            allUserFutures.add(userFuture);

        } // end of user

        try {
            CompletableFuture.allOf(allUserFutures.toArray(new CompletableFuture[0])).join();
            log.info("--- All user recovery tasks have completed. ---");
        } catch (Exception e) {
            log.error("Main recovery thread interrupted while waiting for user tasks.", e);
        } finally {
            outboxEventHandler.cleanUpAllUsers();
            log.info("--- All user tasks are cleaned up ---");
        }


    }

    /** 비동기 완성 코드
    public void allPlaylistsRecoveryOfAllUsers() {

        List<Users> users = userService.findAllUsers();
        List<CompletableFuture<Void>> allUserFutures = new ArrayList<>();

        for (Users user : users) {

            CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                try {
                    String userId = user.getUserId();
                    String countryCode = user.getCountryCode();
                    String refreshToken = user.getRefreshToken();
                    String accessToken = userService.getNewAccessTokenByUserId(userId, refreshToken);
                    log.info("[userId: {}]", userId);

                    if (accessToken.equals("")) {
                        log.info("abort scheduling bc user left");
                        return; // continue
                    }

                    List<Playlists> playListsSet = playlistService.findAllPlaylistsByUserId(userId);

                    for (Playlists playlist : playListsSet) {
                        log.info("[playlistTitle: {}]", playlist.getPlaylistTitle());
                        try {
                            youtubeService.fileTrackAndRecover(userId, playlist, countryCode, accessToken);
                        } catch (QuotaExceededException ex) {
                            log.warn("[Quota Exceed EX at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), ex.getMessage());
                        } catch (Exception e) {
                            log.warn("[Unexpected Error at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), e.getMessage());
                        }
                    }

                    // ----------- FIRST -----------
                    log.info("[Waiting for all Outbox tasks to finish for user {}]", userId);
                    outboxEventHandler.waitForPendingOutboxEvents(userId);
                    log.info("[Waiting done for user {}]", userId);

                    // ----------- RETRY -----------
                    outboxEventHandler.retryFailedOutboxEvents(userId);
                    log.info("[{}] Waiting for retry batch...", userId);
                    outboxEventHandler.waitForPendingOutboxEvents(userId);
                    log.info("[{}] Retry batch complete.", userId);

                } catch (Exception e) {
                    log.error("[{}] Unrecoverable error in user processing thread: {}", user.getUserId(), e.getMessage(), e);
                } finally {
                    // 유저 처리 완료 후 무조건 cleanup
                    outboxEventHandler.cleanUpUser(user.getUserId());
                }
            }, userExecutor);

            allUserFutures.add(userFuture);

        } // end of user

        try {
            CompletableFuture.allOf(allUserFutures.toArray(new CompletableFuture[0])).join();
            log.info("--- All user recovery tasks have completed. ---");
        } catch (Exception e) {
            log.error("Main recovery thread interrupted while waiting for user tasks.", e);
        } finally {
            outboxEventHandler.cleanUpAllUsers();
            log.info("--- All user tasks are cleaned up ---");
        }


    }
    */

    /** TEMP CURRENT CODE THAT WORKED 1105
    public void allPlaylistsRecoveryOfAllUsers() {
        // 0. 전체 유저 목록에서 순차적으로 유저를 뽑기
        List<Users> users = userService.findAllUsers();

        for (Users user : users) {
            String userId = user.getUserId();
            String countryCode = user.getCountryCode();
            String refreshToken = user.getRefreshToken();
            log.info("userId : {}", userId);

            // 1. 유저 아이디로 accessToken 발급
            String accessToken = userService.getNewAccessTokenByUserId(userId, refreshToken);
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
                    youtubeService.fileTrackAndRecover(userId, playlist, countryCode, accessToken);
                } catch (QuotaExceededException ex) {
                    log.warn("[Quota Exceed EX at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), ex.getMessage());
                } catch (Exception e) {// 예상 못한 런타임 에러 방어
                    log.warn("[Unexpected Error at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), e.getMessage());
                }
            }

            log.info("[Waiting for all Outbox tasks to finish for user {}]", userId);
            outboxEventHandler.waitForPendingOutboxEvents(userId); // Outbox 비동기 이벤트 모두 완료될 때까지 대기
            log.info("[Waiting done for user {}]", userId);

//            outboxEventHandler.retryFailedOutboxEvents(userId);
        }

    }
    */

    /*
    public void allPlaylistsRecoveryOfOneParticularUserTest() {

        String userId  = "112735690496635663877";
        String refreshToken = "abc";
        String accessToken = userService.getNewAccessTokenByUserId(userId, refreshToken);
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
     */
}
