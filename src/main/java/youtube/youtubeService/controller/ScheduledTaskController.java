package youtube.youtubeService.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.service.playlists.PlaylistService;
import youtube.youtubeService.service.users.UserService;
import youtube.youtubeService.service.youtube.YoutubeService;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/scheduler")
public class ScheduledTaskController {

    @Value("${youtube.api.key}")
    private String apiKey;
    private final PlaylistService playlistService;
    private final YoutubeService youtubeService;
    private final UserService userService;
    private final SearchPolicy searchPolicy;

    @Autowired
    public ScheduledTaskController( // @Qualifier("simpleSearchQuery")
                                PlaylistService playlistService, YoutubeService youtubeService, UserService userService,
                                @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy) {
        this.playlistService = playlistService;
        this.youtubeService = youtubeService;
        this.userService = userService;
        this.searchPolicy = searchPolicy;
    }

    @PostMapping("/track-recovery")
    public ResponseEntity<String> runDailyTask(@RequestHeader("api-key") String apiKey) throws IOException {
        log.info("track-recovery endpoint triggered");
        if (apiKey == null || !apiKey.equals(this.apiKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid API Key");
        }
        allPlaylistsRecoveryOfOneParticularUserTest();
        log.info("track-recovery endpoint done");
        return ResponseEntity.ok("Auto Track&Recovery Task executed");
    }

    public void allPlaylistsRecoveryOfOneParticularUserTest() throws IOException {
        log.info("auto scheduler activated");
        // 0. 전체 유저 목록에서 순차적으로 유저를 뽑아 오는 시나리오 있다 치고
        String userId  = "112735690496635663877";
        // 1. 유저 아이디로 accessToken 발급
        String accessToken = userService.getNewAccessTokenByUserId(userId);
        // 2. 유저 아이디로 조회한 모든 플레이리스트 & 음악을 디비에서 뽑아서 복구 시스템 가동
        List<Playlists> playListsSet = playlistService.getPlaylistsByUserId(userId);
        for (Playlists playlist : playListsSet) {
            log.info("{} start", playlist.getPlaylistTitle());
            // playlist 자체가 제거된 경우 예외처리 필요
            try {
                youtubeService.fileTrackAndRecover(userId, playlist.getPlaylistId(), accessToken);
            } catch (IOException e) {
                log.info("scheduler caught and then move to next playlist");
            }
        }

        log.info("auto scheduler done");
    }

}
