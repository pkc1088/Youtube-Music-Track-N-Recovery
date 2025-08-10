package youtube.youtubeService.service.playlists;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistListResponse;
import com.google.api.services.youtube.model.Video;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.VideoFilterResult;
import youtube.youtubeService.repository.musics.MusicRepository;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.repository.users.UserRepository;
import lombok.extern.slf4j.Slf4j;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

/** OG Code
public class PlaylistServiceV1 implements PlaylistService {

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final MusicRepository musicRepository;
    private final YoutubeApiClient youtubeApiClient;

    public PlaylistServiceV1(UserRepository userRepository, PlaylistRepository playlistRepository,
                             MusicRepository musicRepository, YoutubeApiClient youtubeApiClient) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
        this.musicRepository = musicRepository;
        this.youtubeApiClient = youtubeApiClient;
    }

    @Override
    public List<Playlists> getPlaylistsByUserId(String userId){
        // DB 에서 userId로 조회 후 리턴
        return playlistRepository.findAllPlaylistsByUserId(userId);
    }

    public List<Playlist> getAllPlaylists(String userId) throws IOException {
        // 1. userId 로 oauth2 인증 거쳐 DB 에 저장됐을 channelId 얻기
        Users user = userRepository.findByUserId(userId);
        String channelId = user.getUserChannelId();
        // 2. channelId로 api 호출 통해 playlist 다 받아오기
        return youtubeApiClient.getApiPlaylists(channelId);
    }

    @Override
    public void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds) {
        // log.info("removePlaylistsFromDB txActive : {} : {}", TransactionSynchronizationManager.isActualTransactionActive(), TransactionSynchronizationManager.getCurrentTransactionName());
        for (String deselectedPlaylist : deselectedPlaylistIds) {
            log.info("playlist({}) is deleted from DB", deselectedPlaylist);
            playlistRepository.deletePlaylistByPlaylistId(deselectedPlaylist);
        }
    }

    public void registerPlaylists(String userId, List<String> selectedPlaylistIds) {//throws IOException
        Users user = userRepository.findByUserId(userId);
        // 1. 전체 플레이리스트 가져오기
        List<Playlist> allPlaylists = null;
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
            playlist.setServiceType("track&recover");
            playlist.setUser(user);
            // 3.1 Playlist 객체를 DB에 저장
            log.info("playlist({}) is added to DB", getPlaylist.getSnippet().getTitle());
            playlistRepository.save(playlist);
            // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
            initiallyAddVideoDetails(getPlaylist.getId());//   musicService.initiallyAddVideoDetails(getPlaylist.getId());
        }
    }

    @Override
    public void initiallyAddVideoDetails(String playlistId) { // throws IOException
        List<PlaylistItem> response = null;
        try {
            response = youtubeApiClient.getPlaylistItemListResponse(playlistId, 50L); // 여긴 굳이 예외 처리 안 해도 됨 <- ?
        } catch (IOException e) {
            log.info("The playlist is deleted in a very short amount of time");
            return;
        }

        for (PlaylistItem item : response) {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            Video video = checkBrokenVideo(videoId);
            if(video == null) {
                log.info("it's a broken video, so passed");
                continue;
            }
            // log.info("initiallyAddVideoDetails try to add : {}", videoId);
            DBAddAction(video, playlistId);
        }
    }

    public Video checkBrokenVideo(String videoId) {
        Video video = null;
        try {
            video = youtubeApiClient.getVideoDetailResponseWithFilter(videoId);
        } catch (IOException e) {
            log.info("A broken video is caught");
            return null;
        }
        return video;
    }

    public void DBAddAction(Video video, String playlistId) {
        Music music = new Music();
        music.setVideoId(video.getId());
        music.setVideoTitle(video.getSnippet().getTitle());
        music.setVideoUploader(video.getSnippet().getChannelTitle());
        music.setVideoDescription(video.getSnippet().getDescription());
        List<String> tags = video.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);
        // log.info("joinedTags : {}", joinedTags);

        music.setPlaylist(playlistRepository.findByPlaylistId(playlistId));
        musicRepository.addUpdatePlaylist(playlistId, music);
    }

}
 */