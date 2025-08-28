package youtube.youtubeService.service.playlists;

import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import lombok.extern.slf4j.Slf4j;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@Transactional
public class PlaylistServiceV1 implements PlaylistService {

    private final UserService userService;
    private final PlaylistRepository playlistRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final MusicService musicService;

    public PlaylistServiceV1(UserService userService, PlaylistRepository playlistRepository, YoutubeApiClient youtubeApiClient, MusicService musicService) {
        this.userService = userService;
        this.playlistRepository = playlistRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.musicService = musicService;
    }

    @Override
    public List<Playlists> getPlaylistsByUserId(String userId){
        // DB 에서 userId로 조회 후 리턴
        return playlistRepository.findAllPlaylistsByUserId(userId);
    }

    @Override
    public List<Playlist> getAllPlaylists(String userId) throws IOException {
        // 1. userId 로 oauth2 인증 거쳐 DB 에 저장됐을 channelId 얻기
        Users user = userService.findByUserId(userId); // userRepository.findByUserId(userId);
        String channelId = user.getUserChannelId();
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        return youtubeApiClient.getApiPlaylists(channelId);
    }

    @Override
    public void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds) {
        for (String deselectedPlaylist : deselectedPlaylistIds) {
            log.info("playlist({}) is deleted from DB", deselectedPlaylist);
            playlistRepository.deletePlaylistByPlaylistId(deselectedPlaylist);
        }
    }

    @Override
    public UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException {
        // 1. API로 사용자의 모든 플레이리스트를 가져옴
        List<Playlist> playlists = getAllPlaylists(userId);
        // 2. DB에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<String> registeredPlaylistIds = getPlaylistsByUserId(userId).stream().map(Playlists::getPlaylistId).toList();

        return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
    }

    @Override
    public void registerPlaylists(PlaylistRegisterRequestDto request) {
        // 1. DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<Playlists> registeredPlaylistIdFromDB = getPlaylistsByUserId(request.getUserId());
        List<String> registeredPlaylistIds = registeredPlaylistIdFromDB.stream().map(Playlists::getPlaylistId).toList();
        // 2. 중복된 플레이리스트는 제외하고 등록
        List<String> newlySelectedPlaylistIds = request.getSelectedPlaylistIds().stream().filter(playlistId -> !registeredPlaylistIds.contains(playlistId)).toList();

        if (!newlySelectedPlaylistIds.isEmpty()) {
            registerSelectedPlaylists(request.getUserId(), newlySelectedPlaylistIds);  // 중복되지 않는 플레이리스트만 등록
        }

        if (request.getDeselectedPlaylistIds() != null && !request.getDeselectedPlaylistIds().isEmpty()) {
            removePlaylistsFromDB(request.getUserId(), request.getDeselectedPlaylistIds()); // 체크 해제된 플레이리스트 DB에서 삭제
        }

        log.info("[Registering Playlists Is Completed]");
    }

    private void registerSelectedPlaylists(String userId, List<String> selectedPlaylistIds) {//throws IOException
        Users user = userService.findByUserId(userId); // userRepository.findByUserId(userId);
        String countryCode = user.getCountryCode();
        // 1. 전체 플레이리스트 가져오기
        List<Playlist> allPlaylists;
        try {
            allPlaylists = getAllPlaylists(userId);
        } catch (IOException e) {
            log.info("The user is deleted in a very short amount of time");
            return;
        }
        // 2. 선택된 ID에 해당하는 Playlist 만 필터링
        List<Playlist> selectedPlaylists = allPlaylists.stream().filter(p -> selectedPlaylistIds.contains(p.getId())).toList();
        // 3. playlists, music 도메인에 저장하기
        for (Playlist getPlaylist : selectedPlaylists) {
            Playlists playlist = new Playlists();
            playlist.setPlaylistId(getPlaylist.getId());
            playlist.setPlaylistTitle(getPlaylist.getSnippet().getTitle());
            playlist.setServiceType(Playlists.ServiceType.RECOVER);
            playlist.setUser(user);
            // 3.1 Playlist 객체를 DB에 저장
            log.info("playlist({}) is added to DB", getPlaylist.getSnippet().getTitle());
            playlistRepository.save(playlist);
            // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
            initiallyAddVideoDetails(getPlaylist.getId(), countryCode);//   musicService.initiallyAddVideoDetails(getPlaylist.getId());
        }
    }

    private void initiallyAddVideoDetails(String playlistId, String countryCode) {
        List<PlaylistItem> playlistItems;
        try {
            // 1. 페이지네이션으로 플레이리스트 내용물 전체 확보 (비정상도 다 포함)
            playlistItems = youtubeApiClient.getPlaylistItemListResponse(playlistId, 50L);
        } catch (IOException e) {
            log.info("The playlist is deleted in a very short amount of time");
            return;
        }
        // 2. 검색할 비디오 ID 리스트 만들기
        List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
        // 3. 페이지네이션으로 정상 음악만 세부사항 필터링 해오기
        List<Video> legalVideos = youtubeApiClient.safeFetchVideos(videoIds, countryCode).getLegalVideos();
        // 4. DB 에 최초 등록

        Playlists playlist = playlistRepository.findByPlaylistId(playlistId);
        musicService.saveAll(legalVideos, playlist);
    }

}

/*
@Slf4j
@Service
@Transactional
public class PlaylistServiceV1 implements PlaylistService {

    private final UserService userService;
    private final PlaylistRepository playlistRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final MusicService musicService;

    public PlaylistServiceV1(UserService userService, PlaylistRepository playlistRepository,
                             YoutubeApiClient youtubeApiClient, MusicService musicService) {
        this.userService = userService;
        this.playlistRepository = playlistRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.musicService = musicService;
    }

    @Override
    public List<Playlists> getPlaylistsByUserId(String userId){
        // DB 에서 userId로 조회 후 리턴
        return playlistRepository.findAllPlaylistsByUserId(userId);
    }

    @Override
    public List<Playlist> getAllPlaylists(String userId) throws IOException {
        // 1. userId 로 oauth2 인증 거쳐 DB 에 저장됐을 channelId 얻기
        Users user = userService.findByUserId(userId); // userRepository.findByUserId(userId);
        String channelId = user.getUserChannelId();
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        return youtubeApiClient.getApiPlaylists(channelId);
    }

    @Override
    public void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds) {
        for (String deselectedPlaylist : deselectedPlaylistIds) {
            log.info("playlist({}) is deleted from DB", deselectedPlaylist);
            playlistRepository.deletePlaylistByPlaylistId(deselectedPlaylist);
        }
    }

    @Override
    public void registerPlaylists(String userId, List<String> selectedPlaylistIds) {//throws IOException
        Users user = userService.findByUserId(userId); // userRepository.findByUserId(userId);
        String countryCode = user.getCountryCode();
        // 1. 전체 플레이리스트 가져오기
        List<Playlist> allPlaylists;
        try {
            allPlaylists = getAllPlaylists(userId);
        } catch (IOException e) {
            log.info("The user is deleted in a very short amount of time");
            return;
        }
        // 2. 선택된 ID에 해당하는 Playlist 만 필터링
        List<Playlist> selectedPlaylists = allPlaylists.stream().filter(p -> selectedPlaylistIds.contains(p.getId())).toList();
        // 3. playlists, music 도메인에 저장하기
        for (Playlist getPlaylist : selectedPlaylists) {
            Playlists playlist = new Playlists();
            playlist.setPlaylistId(getPlaylist.getId());
            playlist.setPlaylistTitle(getPlaylist.getSnippet().getTitle());
            playlist.setServiceType(Playlists.ServiceType.RECOVER);
            playlist.setUser(user);
            // 3.1 Playlist 객체를 DB에 저장
            log.info("playlist({}) is added to DB", getPlaylist.getSnippet().getTitle());
            playlistRepository.save(playlist);
            // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
            initiallyAddVideoDetails(getPlaylist.getId(), countryCode);//   musicService.initiallyAddVideoDetails(getPlaylist.getId());
        }
    }

//    @Override
    public void initiallyAddVideoDetails(String playlistId, String countryCode) {
        List<PlaylistItem> playlistItems;
        try {
            // 1. 페이지네이션으로 플레이리스트 내용물 전체 확보 (비정상도 다 포함)
            playlistItems = youtubeApiClient.getPlaylistItemListResponse(playlistId, 50L);
        } catch (IOException e) {
            log.info("The playlist is deleted in a very short amount of time");
            return;
        }
        // 2. 검색할 비디오 ID 리스트 만들기
        List<String> videoIds = playlistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();
        // 3. 페이지네이션으로 정상 음악만 세부사항 필터링 해오기
        List<Video> legalVideos = youtubeApiClient.safeFetchVideos(videoIds, countryCode).getLegalVideos();
        // 4. DB 에 최초 등록

        Playlists playlist = playlistRepository.findByPlaylistId(playlistId);
        musicService.saveAll(legalVideos, playlist);
    }

}
 */