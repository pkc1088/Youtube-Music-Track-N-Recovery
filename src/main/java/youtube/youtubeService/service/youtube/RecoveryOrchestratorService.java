package youtube.youtubeService.service.youtube;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.MusicSummaryDto;
import youtube.youtubeService.dto.internal.PlaylistRecoveryPlanDto;
import youtube.youtubeService.exception.NoPlaylistFoundException;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.exception.UserQuitException;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.outbox.OutboxEventHandler;
import youtube.youtubeService.service.playlists.PlaylistPersistenceService;
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
public class RecoveryOrchestratorService {

    private final UserService userService;
    private final PlaylistService playlistService;
    private final MusicService musicService;
    private final OutboxEventHandler outboxEventHandler;
    private final RecoveryPlanService recoveryPlanService;
    private final RecoveryExecuteService recoveryExecuteService;
    private final PlaylistPersistenceService playlistPersistenceService;
    private final Executor userExecutor;

    public void allPlaylistsRecoveryOfAllUsers() {

        List<Users> users = userService.findAllUsers();
        List<String> userIds = users.stream().map(Users::getUserId).toList();
        List<Playlists> allPlaylists = playlistService.findAllPlaylistsByUserIds(userIds); // JOIN FETCH
        Map<String, List<Playlists>> playlistsByUser = allPlaylists.stream().collect(
                Collectors.groupingBy(playlist -> playlist.getUser().getUserId())          // N+1 발생 안 함 (위에서 join fetch 했으니)
        );

        List<CompletableFuture<Void>> allUserFutures = new ArrayList<>();

        for (Users user : users) {

            CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                try {
                    String accessToken;
                    String userId = user.getUserId();
                    String countryCode = user.getCountryCode();
                    String refreshToken = user.getRefreshToken();

                    try {
                        log.info("[userId: {}]", userId);
                        accessToken = userService.getNewAccessTokenByUserId(userId, refreshToken);
                    } catch (UserQuitException e) {
                        log.warn("[User Left] Executing withdrawal process on user {}", userId);
                        userService.deleteByUserIdRaw(userId);
                        log.info("[Abort scheduling bc user left -> skip to the user]");
                        return;
                    }

                    List<Playlists> playListsSet = playlistsByUser.getOrDefault(userId, Collections.emptyList()); // DB 조회가 아닌, 메모리 Map 에서 조회

                    List<MusicSummaryDto> allMusicForUser = musicService.findAllMusicSummaryByPlaylistIds(playListsSet);

                    Map<String, List<MusicSummaryDto>> musicMapByPlaylistId = allMusicForUser.stream().collect(
                            Collectors.groupingBy(MusicSummaryDto::playlistId)
                    );

                    for (Playlists playlist : playListsSet) {

                        log.info("[Playlist: {}({})]", playlist.getPlaylistTitle(), playlist.getPlaylistId());
                        List<MusicSummaryDto> musicForThisPlaylist = musicMapByPlaylistId.getOrDefault(playlist.getPlaylistId(), Collections.emptyList());

                        try {
                            PlaylistRecoveryPlanDto plans = recoveryPlanService.prepareRecoveryPlan(userId, playlist, countryCode, accessToken, musicForThisPlaylist);
                            // 저기서 FTR no Tx -> updatePlaylist no Tx 거쳐서 나온 전체 명세서 받음
                            if (plans.hasActions()) {
                                recoveryExecuteService.executeRecoveryPlan(userId, playlist, accessToken, plans);
                            } // 아무 plan 없으면 그냥 어제와 똑같은 상황이라 손댈 필요 x 바로 스킵

                        } catch (QuotaExceededException ex) {
                            log.warn("[Quota Exceed EX at playlist({}), {} -> skip to the next]", playlist.getPlaylistId(), ex.getMessage());
                        } catch (NoPlaylistFoundException npe) {
                            playlistPersistenceService.removeDeselectedPlaylists(Collections.singletonList(playlist.getPlaylistId()));
                            log.warn("[NoPlaylistFoundException is successfully handled -> skip to the next]");
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

}
