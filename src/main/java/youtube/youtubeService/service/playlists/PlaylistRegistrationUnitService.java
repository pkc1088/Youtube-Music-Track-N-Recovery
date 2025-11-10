package youtube.youtubeService.service.playlists;

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.PlaylistDto;
import youtube.youtubeService.dto.QuotaApiPlaylistsPageDto;
import youtube.youtubeService.dto.QuotaPlaylistItemPageDto;
import youtube.youtubeService.dto.VideoFilterResultPageDto;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.users.UserService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistRegistrationUnitService {
    private final YoutubeApiClient youtubeApiClient;
    private final MusicService musicService;
    private final QuotaService quotaService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(REQUIRES_NEW)
    public void saveSinglePlaylist(PlaylistDto playlistDto, String userId, String countryCode) {

        Playlists playlist = new Playlists();
        playlist.setPlaylistId(playlistDto.getId());
        playlist.setPlaylistTitle(playlistDto.getTitle());
        playlist.setServiceType(Playlists.ServiceType.RECOVER);

        Users userRef = entityManager.getReference(Users.class, userId); // 불필요한 playlist 관련 select 쿼리 1회 막는 용(바로 persist)
        playlist.setUser(userRef);
        entityManager.persist(playlist);
        entityManager.flush(); // bulk insert 용
        saveAllVideos(userId, playlist, countryCode);
    }

    private void saveAllVideos(String userId, Playlists playlist, String countryCode) {
        List<PlaylistItem> playlistItems;
        try {
            playlistItems = fetchAllPlaylistItems(userId, playlist.getPlaylistId());
        } catch (IOException e) {
            log.info("The playlist is deleted in a very short amount of time");
            return; // 이미 진입전에 1 소모한걸 반영했으니 여기선 처리 필요 x
        }
        // 2. 검색할 비디오 ID 리스트 만들기
        List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
        // 3. 페이지네이션으로 정상 음악만 세부사항 필터링 해오기
        VideoFilterResultPageDto videoFilterResult = fetchAllVideos(userId, videoIds, countryCode);
        List<Video> legalVideos = videoFilterResult.getLegalVideos();
        // 4. DB 에 최초 등록
        musicService.saveAllVideos(legalVideos, playlist);
    }

    public List<PlaylistItem> fetchAllPlaylistItems(String userId, String playlistId) throws IOException {
        List<PlaylistItem> playlistItems = new ArrayList<>();
        String nextPageToken = null;

        do {
            // log.info("[paginationPlaylistItemList]: trying to add 1 quota");
            if (!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("Quota Exceed");

            QuotaPlaylistItemPageDto dto = youtubeApiClient.fetchPlaylistItemPage(playlistId, nextPageToken);
            playlistItems.addAll(dto.getAllPlaylists());
            nextPageToken = dto.getNextPageToken();
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
            // log.info("[paginationVideoFilterResult]: trying to add 1 quota");
            if(!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("Quota Exceed");

            int fromIndex = i * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, total);
            List<String> videoIdsBatch = videoIds.subList(fromIndex, toIndex);
            VideoFilterResultPageDto videoFilterResult = youtubeApiClient.fetchVideoPage(videoIdsBatch, countryCode);
            legalVideos.addAll(videoFilterResult.getLegalVideos());
            unlistedCountryVideos.addAll(videoFilterResult.getUnlistedCountryVideos());
        }

        return new VideoFilterResultPageDto(legalVideos, unlistedCountryVideos);
    }

    public List<Playlist> fetchAllPlaylists(String userId, String channelId) throws IOException {
        List<Playlist> playlists = new ArrayList<>();
        String nextPageToken = null;
        do {
            // log.info("[getAllPlaylists -> getApiPlaylistsByPage]: trying to add 1 quota");
            if(!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("Quota Exceed");

            QuotaApiPlaylistsPageDto dto = youtubeApiClient.fetchPlaylistPage(channelId, nextPageToken);
            playlists.addAll(dto.getPlaylists());
            nextPageToken = dto.getNextPageToken();
        } while (nextPageToken != null);

        return playlists;
    }
}

/** OG CODE BEFORE 1111
     @Slf4j
     @Service
     @RequiredArgsConstructor
     public class PlaylistRegistrationUnitService {
     private final PlaylistRepository playlistRepository;
     private final YoutubeApiClient youtubeApiClient;
     private final MusicService musicService;
     private final QuotaService quotaService;

     @Transactional(REQUIRES_NEW)
     public void saveSinglePlaylist(Playlists playlist, String userId, String countryCode) {
     Playlists managedPlaylist = playlistRepository.save(playlist);
     saveAllVideos(userId, managedPlaylist, countryCode);
     }

     private void saveAllVideos(String userId, Playlists playlist, String countryCode) {
     List<PlaylistItem> playlistItems;
     try {
     playlistItems = fetchAllPlaylistItems(userId, playlist.getPlaylistId());
     } catch (IOException e) {
     log.info("The playlist is deleted in a very short amount of time");
     return; // 이미 진입전에 1 소모한걸 반영했으니 여기선 처리 필요 x
     }
     // 2. 검색할 비디오 ID 리스트 만들기
     List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
     // 3. 페이지네이션으로 정상 음악만 세부사항 필터링 해오기
     VideoFilterResultPageDto videoFilterResult = fetchAllVideos(userId, videoIds, countryCode);
     List<Video> legalVideos = videoFilterResult.getLegalVideos();
     // 4. DB 에 최초 등록
     // Playlists playlist = playlistRepository.findByPlaylistId(playlistId);
     musicService.saveAllVideos(legalVideos, playlist);
     }

     public List<PlaylistItem> fetchAllPlaylistItems(String userId, String playlistId) throws IOException {
     List<PlaylistItem> playlistItems = new ArrayList<>();
     String nextPageToken = null;

     do {
     // log.info("[paginationPlaylistItemList]: trying to add 1 quota");
     if (!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("Quota Exceed");

     QuotaPlaylistItemPageDto dto = youtubeApiClient.fetchPlaylistItemPage(playlistId, nextPageToken);
     playlistItems.addAll(dto.getAllPlaylists());
     nextPageToken = dto.getNextPageToken();
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
     // log.info("[paginationVideoFilterResult]: trying to add 1 quota");
     if(!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("Quota Exceed");

     int fromIndex = i * batchSize;
     int toIndex = Math.min(fromIndex + batchSize, total);
     List<String> videoIdsBatch = videoIds.subList(fromIndex, toIndex);
     VideoFilterResultPageDto videoFilterResult = youtubeApiClient.fetchVideoPage(videoIdsBatch, countryCode);
     legalVideos.addAll(videoFilterResult.getLegalVideos());
     unlistedCountryVideos.addAll(videoFilterResult.getUnlistedCountryVideos());
     }

     return new VideoFilterResultPageDto(legalVideos, unlistedCountryVideos);
     }

     public List<Playlist> fetchAllPlaylists(String userId, String channelId) throws IOException {
     List<Playlist> playlists = new ArrayList<>();
     String nextPageToken = null;
     do {
     // log.info("[getAllPlaylists -> getApiPlaylistsByPage]: trying to add 1 quota");
     if(!quotaService.checkAndConsumeLua(userId, QuotaType.PAGINATION.getCost())) throw new QuotaExceededException("Quota Exceed");

     QuotaApiPlaylistsPageDto dto = youtubeApiClient.fetchPlaylistPage(channelId, nextPageToken);
     playlists.addAll(dto.getPlaylists());
     nextPageToken = dto.getNextPageToken();
     } while (nextPageToken != null);

     return playlists;
     }
     }
 */

/** OGCODE BEFORE 1109
 @Transactional(REQUIRES_NEW)
 public void saveSinglePlaylist(Playlists playlist, String userId, String playlistId, String countryCode) {
 playlistRepository.save(playlist);
 saveAllVideos(userId, playlistId, countryCode);
 }

 private void saveAllVideos(String userId, String playlistId, String countryCode) {
 List<PlaylistItem> playlistItems;
 try {
 playlistItems = fetchAllPlaylistItems(userId, playlistId);
 } catch (IOException e) {
 log.info("The playlist is deleted in a very short amount of time");
 return; // 이미 진입전에 1 소모한걸 반영했으니 여기선 처리 필요 x
 }
 // 2. 검색할 비디오 ID 리스트 만들기
 List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
 // 3. 페이지네이션으로 정상 음악만 세부사항 필터링 해오기
 VideoFilterResultPageDto videoFilterResult = fetchAllVideos(userId, videoIds, countryCode);
 List<Video> legalVideos = videoFilterResult.getLegalVideos();
 // 4. DB 에 최초 등록
 Playlists playlist = playlistRepository.findByPlaylistId(playlistId);
 musicService.saveAllVideos(legalVideos, playlist);
 }
 */