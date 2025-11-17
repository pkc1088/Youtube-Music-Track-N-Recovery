package youtube.youtubeService.service.playlists;

import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.MusicSummaryDto;
import youtube.youtubeService.dto.PlannedPlaylistUpdateDto;
import youtube.youtubeService.dto.VideoFilterResultPageDto;
import youtube.youtubeService.exception.NoPlaylistFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistStateCheckService {

    private final PlaylistRegistrationUnitService playlistRegistrationUnitService;

    public PlannedPlaylistUpdateDto compareApiAndDbState(String userId, String countryCode, Playlists playlist, List<MusicSummaryDto> pureDbMusicList) throws IOException {
        String playlistId = playlist.getPlaylistId();
        log.info("[Check playlist start: {}]", playlistId);
        List<PlaylistItem> pureApiPlaylistItems;

        try {
            pureApiPlaylistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(userId, playlistId);
        } catch (IOException e) {
            // removePlaylistsFromDB 이거 오케에서 호출하도록 밖으로 뺏음.
            log.warn("[This playlist has been removed by the owner({})]", playlistId);
            throw new NoPlaylistFoundException("[no playlist found exception]");
        }

        // 3. API 에서 video 상태 조회
        List<String> pureApiVideoIds = pureApiPlaylistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();

        VideoFilterResultPageDto videoFilterResult = playlistRegistrationUnitService.fetchAllVideos(userId, pureApiVideoIds, countryCode);
        List<Video> legalVideos = videoFilterResult.getLegalVideos();
        List<Video> unlistedCountryVideos = videoFilterResult.getUnlistedCountryVideos();

        // 4-1. 정상 비디오
        List<String> legalVideoIds = legalVideos.stream().map(Video::getId).toList();
        // 4-2. unlisted, 국가차단 비디오
        List<String> unlistedCountryVideoIds = unlistedCountryVideos.stream().map(Video::getId).toList();
        // 4-3. Delete / Private 비디오 (응답 자체가 안 온 videoId)
        List<String> privateDeletedVideoIds = pureApiVideoIds.stream().filter(videoId -> !legalVideoIds.contains(videoId) && !unlistedCountryVideoIds.contains(videoId)).toList();

        log.info("[legal] videos count : {}", legalVideoIds.size());
        log.info("[unlisted/country] video count : {}", unlistedCountryVideoIds.size());
        log.info("[deleted/private] video count : {}", privateDeletedVideoIds.size());
        // 5. 둘의 차이를 비교 → DB 반영
        Map<String, Long> apiCounts = pureApiVideoIds.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        Map<String, Long> dbCounts = pureDbMusicList.stream().collect(Collectors.groupingBy(MusicSummaryDto::videoId, Collectors.counting()));

        Set<String> allVideoIds = new HashSet<>(); // 영상 개수가 중요하진 x, 둘을 모두 순회하기 위해 담는 것일 뿐임
        allVideoIds.addAll(apiCounts.keySet());
        allVideoIds.addAll(dbCounts.keySet());

        List<Video> videosToInsert = new ArrayList<>();
        List<Long> videosToDelete = new ArrayList<>();

        for (String videoId : allVideoIds) {

            long apiCount = apiCounts.getOrDefault(videoId, 0L); //2
            long dbCount = dbCounts.getOrDefault(videoId, 0L); // 1

            long toInsertCount = apiCount - dbCount; // 1
            long toDeleteCount = dbCount - apiCount; // -1

            if (toInsertCount > 0 && legalVideoIds.contains(videoId)) { // toInsertCount 만큼 DBAddAction 을 반복
                legalVideos.stream().filter(v -> v.getId().equals(videoId)).findFirst()
                        .ifPresent(video -> IntStream.range(0, (int) toInsertCount).forEach(i -> videosToInsert.add(video)));
            }

            if (toDeleteCount > 0 && !unlistedCountryVideoIds.contains(videoId) && !privateDeletedVideoIds.contains(videoId)) { // 삭제할 개수만큼만 제한 후 Music 객체에서 ID만 추출 각 ID를 사용하여 삭제
                pureDbMusicList.stream().filter(m -> m.videoId().equals(videoId)).limit(toDeleteCount)
                        .map(MusicSummaryDto::id).forEach(videosToDelete::add); // pk 추가임
            }
        }

        // 이제 여기서 DB 호출 X

        Map<String, List<String>> illegalVideos = new HashMap<>();

        for (PlaylistItem item : pureApiPlaylistItems) {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            String playlistItemId = item.getId();

            if (unlistedCountryVideoIds.contains(videoId) || privateDeletedVideoIds.contains(videoId)) {
                illegalVideos.computeIfAbsent(videoId, k -> new ArrayList<>()).add(playlistItemId);
            }
        }

        log.info("[Check playlist done: {}]", playlistId);
        return new PlannedPlaylistUpdateDto(videosToInsert, videosToDelete, illegalVideos);
    }
}
