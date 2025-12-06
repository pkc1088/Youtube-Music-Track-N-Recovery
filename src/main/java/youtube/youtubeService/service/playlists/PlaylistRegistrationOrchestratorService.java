package youtube.youtubeService.service.playlists;

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.FixedDataForRegistrationDto;
import youtube.youtubeService.dto.internal.PlaylistDto;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.dto.request.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.response.PlaylistRegisterResponseDto;
import youtube.youtubeService.dto.response.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.exception.quota.QuotaExceededException;
import youtube.youtubeService.exception.youtube.ChannelNotFoundException;
import youtube.youtubeService.service.musics.MusicConverterHelper;
import youtube.youtubeService.service.users.UserTokenService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistRegistrationOrchestratorService {

    private final PlaylistRegistrationUnitService playlistRegistrationUnitService;
    private final PlaylistPersistenceService playlistPersistenceService;
    private final MusicConverterHelper musicConverterHelper;
    private final UserTokenService userTokenService;
    private final PlaylistService playlistService;


    public UserRegisterPlaylistsResponseDto processPlaylistSelection(String userId, String channelId) {
        // DB 에서 사용자가 이미 등록한 플레이리스트 조회
        List<Playlists> registeredPlaylists = playlistService.findAllByUserIdWithUserFetch(userId);
        List<String> registeredPlaylistIds = registeredPlaylists.stream().map(Playlists::getPlaylistId).toList();

        // API 로 사용자의 모든 플레이리스트 조회
        List<Playlist> playlists;
        try {
            playlists = playlistRegistrationUnitService.fetchAllPlaylists(userId, channelId);
        } catch (ChannelNotFoundException e) {
            log.warn("[User Cleanup] User {} has NO channel. Deleting user...", userId);
            userTokenService.withdrawUser(userId);
            throw e;
        }

        return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
    }

    public PlaylistRegisterResponseDto processPlaylistRegistration(PlaylistRegisterRequestDto request, String countryCode) {
        // DB 에서 사용자가 이미 등록한 플레이리스트 조회
        List<Playlists> registeredPlaylists = playlistService.findAllByUserIdWithUserFetch(request.userId()); // playlistRepository.findAllByUserIdWithUserFetch(userId);
        List<String> registeredPlaylistIds = registeredPlaylists.stream().map(Playlists::getPlaylistId).toList();
        Users userRef = Users.createReference(request.userId());

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
                Playlists playlist = new Playlists(playlistDto.id(), playlistDto.title(), Playlists.ServiceType.RECOVER, LocalDateTime.now(), userRef);
                List<Music> musicList = collectMusicsForPlaylist(playlist, countryCode);
                accumulatedFixedDataForRegistrationDto.add(new FixedDataForRegistrationDto(playlist, musicList));
            }
        } catch (QuotaExceededException qex) { // 얜 여기서 부분 성공을 위해 잡아줘야함
            log.info("QEX during fetching musics for registration.. save accumulated data so far and abort the rests");
        }

        // 확보된 플레이리스트 및 음악을 실제로 등록 및 제거 (다른 클래스로 빼야함 트잭 먹이려면)
        if(!deselectedPlaylists.isEmpty() || !accumulatedFixedDataForRegistrationDto.isEmpty()) {
            playlistPersistenceService.syncPlaylists(deselectedPlaylists, accumulatedFixedDataForRegistrationDto);
        }

        // 컨트롤러(클라이언트)에 결과값(얼만큼의 플레이리스트 등록에 성공했는지 반환)
        return new PlaylistRegisterResponseDto(accumulatedFixedDataForRegistrationDto.size(), newlySelectedPlaylists.size());
    }

    private List<Music> collectMusicsForPlaylist(Playlists playlist, String countryCode) {
        List<PlaylistItem> playlistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(playlist.getUser().getUserId(), playlist.getPlaylistId());
        List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
        VideoFilterResultPageDto videoFilterResult = playlistRegistrationUnitService.fetchAllVideos(playlist.getUser().getUserId(), videoIds, countryCode);
        List<Video> legalVideos = videoFilterResult.legalVideos();

        return legalVideos.stream().map(video -> musicConverterHelper.makeVideoToMusic(video, playlist)).toList();
    }
}
