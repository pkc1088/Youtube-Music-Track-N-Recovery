package youtube.youtubeService.service.playlists;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.PlaylistDto;
import youtube.youtubeService.dto.internal.FixedDataForRegistrationDto;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.dto.request.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.response.PlaylistRegisterResponseDto;
import youtube.youtubeService.dto.response.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.musics.MusicConverterHelper;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistRegistrationOrchestratorService {

    private final PlaylistRegistrationUnitService playlistRegistrationUnitService;
    private final PlaylistPersistenceService playlistPersistenceService;
    private final MusicConverterHelper musicConverterHelper;
    private final PlaylistRepository playlistRepository;
    private final UserService userService;


    private Users findUserOptimized(String userId, List<Playlists> fetchedPlaylists) {
        if (fetchedPlaylists == null || fetchedPlaylists.isEmpty()) {
            // (쿼리 2 - A) 등록된 플리가 없으면(신규 유저 등), User 를 별도 조회
            return userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
        } else {
            // (쿼리 2 - B) 등록된 플리가 있다면, 쿼리 1에서 JOIN FETCH 한 User 객체를 재사용 (추가 쿼리 없음)
            return fetchedPlaylists.get(0).getUser();
        }
    }

    public UserRegisterPlaylistsResponseDto processPlaylistSelection(String userId) throws IOException {
        // DB 에서 사용자가 이미 등록한 플레이리스트 조회
        List<Playlists> registeredPlaylists = playlistRepository.findAllByUserIdWithUserFetch(userId);
        List<String> registeredPlaylistIds = registeredPlaylists.stream().map(Playlists::getPlaylistId).toList();
        Users user = findUserOptimized(userId, registeredPlaylists);
        // API 로 사용자의 모든 플레이리스트 조회
        List<Playlist> playlists = playlistRegistrationUnitService.fetchAllPlaylists(userId, user.getUserChannelId());
        return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
    }

    /**
     * No Transaction - PlaylistService registerPlaylists() 대체
     */
    public PlaylistRegisterResponseDto processPlaylistRegistration(PlaylistRegisterRequestDto request) {
        // DB 에서 사용자가 이미 등록한 플레이리스트 조회
        List<Playlists> registeredPlaylists = playlistRepository.findAllByUserIdWithUserFetch(request.userId());
        List<String> registeredPlaylistIds = registeredPlaylists.stream().map(Playlists::getPlaylistId).toList();
        Users user = findUserOptimized(request.userId(), registeredPlaylists);
        // 중복된 플레이리스트는 제외하고 신규 플레이리스트만 필터
        List<String> deselectedPlaylists = request.deselectedPlaylistIds();
        List<PlaylistDto> newlySelectedPlaylists = request.getSelectedPlaylists().stream()
                                                    .filter(dto -> dto != null && !dto.id().isBlank())
                                                    .filter(dto -> !registeredPlaylistIds.contains(dto.id())).toList();

        // API 로 플레이리스트에 딸린 음악을 최대한의 뽑아온다
        List<FixedDataForRegistrationDto> accumulatedFixedDataForRegistrationDto = new ArrayList<>();
        try {
            for (PlaylistDto playlistDto : newlySelectedPlaylists) {
                // 하나의 등록 대기 플레이리스트에 딸린 모든 음악 데이터 다 수집 시도, 일단 비영속 playlist 객체 하나 만들어 놓고, 저장은 어차피 나중에 다른 곳에서 할거임
                Playlists playlist = new Playlists(playlistDto.id(), playlistDto.title(), Playlists.ServiceType.RECOVER, user);
                List<Music> musicList = collectMusicsForPlaylist(playlist);
                accumulatedFixedDataForRegistrationDto.add(new FixedDataForRegistrationDto(playlist, musicList));
            }
        } catch (QuotaExceededException qex) {
            log.info("QEX during fetching musics for registration.. save accumulated data so far and abort the rests");
        } catch (IOException ex) {
            log.info("Unexpected error occurred");
        }

        // 확보된 플레이리스트 및 음악을 실제로 등록 및 제거 (다른 클래스로 빼야함 트잭 먹이려면)
        if(!deselectedPlaylists.isEmpty() || !accumulatedFixedDataForRegistrationDto.isEmpty()) {
            playlistPersistenceService.syncPlaylists(deselectedPlaylists, accumulatedFixedDataForRegistrationDto);
        }

        // 컨트롤러(클라이언트)에 결과값(얼만큼의 플레이리스트 등록에 성공했는지 반환)
        return new PlaylistRegisterResponseDto(accumulatedFixedDataForRegistrationDto.size(), newlySelectedPlaylists.size());
    }

    private List<Music> collectMusicsForPlaylist(Playlists playlist) throws IOException {
        List<PlaylistItem> playlistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(playlist.getUser().getUserId(), playlist.getPlaylistId()/*selectedPlaylistDto.getId()*/);
        List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
        VideoFilterResultPageDto videoFilterResult = playlistRegistrationUnitService.fetchAllVideos(playlist.getUser().getUserId(), videoIds, playlist.getUser().getCountryCode());
        List<Video> legalVideos = videoFilterResult.legalVideos();

        return legalVideos.stream().map(video -> musicConverterHelper.makeVideoToMusic(video, playlist)).toList();
    }
}
