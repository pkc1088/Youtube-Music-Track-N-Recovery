package youtube.youtubeService.service.playlists;

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.internal.QuotaApiPlaylistsPageDto;
import youtube.youtubeService.dto.internal.QuotaPlaylistItemPageDto;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.exception.quota.QuotaExceededException;
import youtube.youtubeService.service.QuotaService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistRegistrationUnitService {

    private final YoutubeApiClient youtubeApiClient;
    private final QuotaService quotaService;


    public List<PlaylistItem> fetchAllPlaylistItems(String userId, String playlistId) {
        List<PlaylistItem> playlistItems = new ArrayList<>();
        String nextPageToken = null;

        do {
            if (!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("fetchAllPlaylistItems");

            QuotaPlaylistItemPageDto dto = youtubeApiClient.fetchPlaylistItemPage(playlistId, nextPageToken);
            playlistItems.addAll(dto.allPlaylists());
            nextPageToken = dto.nextPageToken();
        } while (nextPageToken != null);

        return playlistItems;
    }

    public VideoFilterResultPageDto fetchAllVideos(String userId, List<String> videoIds, String countryCode) {
        int batchSize = 50;
        int total = videoIds.size();
        int pagination = (int) Math.ceil((double) total / batchSize);

        List<Video> legalVideos = new ArrayList<>();
        List<Video> unlistedCountryVideos = new ArrayList<>();

        for (int i = 0; i < pagination; i++) {
            if(!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("fetchAllVideos");

            int fromIndex = i * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, total);
            List<String> videoIdsBatch = videoIds.subList(fromIndex, toIndex);
            VideoFilterResultPageDto videoFilterResult = youtubeApiClient.fetchVideoPage(videoIdsBatch, countryCode);
            legalVideos.addAll(videoFilterResult.legalVideos());
            unlistedCountryVideos.addAll(videoFilterResult.unlistedCountryVideos());
        }

        return new VideoFilterResultPageDto(legalVideos, unlistedCountryVideos);
    }

    public List<Playlist> fetchAllPlaylists(String userId, String channelId) {
        List<Playlist> playlists = new ArrayList<>();
        String nextPageToken = null;
        do {
            if(!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("fetchAllPlaylists");

            QuotaApiPlaylistsPageDto dto = youtubeApiClient.fetchPlaylistPage(channelId, nextPageToken);
            playlists.addAll(dto.playlists());
            nextPageToken = dto.nextPageToken();
        } while (nextPageToken != null);

        return playlists;
    }
}

