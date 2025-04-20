package youtube.youtubeService.service.playlists;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.repository.users.UserRepository;
import youtube.youtubeService.service.musics.MusicService;
import lombok.extern.slf4j.Slf4j;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class PlaylistServiceV1 implements PlaylistService {

    @Value("${youtube.api.key}")
    private String apiKey;
    private static YouTube youtube;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final MusicService musicService;

    public PlaylistServiceV1(UserRepository userRepository, PlaylistRepository playlistRepository, MusicService musicService) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
        this.musicService = musicService;
        youtube = new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), request -> {}).setApplicationName("youtube").build();
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
        YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
        request.setKey(apiKey);
        request.setChannelId(channelId);
        request.setMaxResults(50L);

        // page
        List<Playlist> allPlaylists = new ArrayList<>();
        String nextPageToken = null;
        do {
            request.setPageToken(nextPageToken); // 다음 페이지 토큰 설정
            PlaylistListResponse response = request.execute();
            allPlaylists.addAll(response.getItems());
            nextPageToken = response.getNextPageToken();
        } while (nextPageToken != null); // 더 이상 페이지가 없을 때까지 반복

        return allPlaylists;
    }

    public void registerPlaylists(String userId, List<String> selectedPlaylistIds) throws IOException {
        Users user = userRepository.findByUserId(userId);

        // 1. 전체 플레이리스트 가져오기
        List<Playlist> allPlaylists = getAllPlaylists(userId);
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
            playlistRepository.save(playlist);
            // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
            musicService.initiallyAddVideoDetails(getPlaylist.getId());
        }
    }



//    25.04.12 주석처리
//    public void registerPlaylists(String userId) throws IOException {
//        // 1. userId 로 oauth2 인증 거쳐 DB 에 저장됐을 channelId 얻기
//        Users user = userRepository.findByUserId(userId);
//        String channelId = user.getUserChannelId();
//        // 2. channelId로 api 호출 통해 playlist 다 받아오기
//        YouTube.Playlists.List request = youtube.playlists().list(Collections.singletonList("snippet, id, contentDetails"));
//        request.setKey(apiKey);
//        request.setChannelId(channelId);
//        request.setMaxResults(50L);
//        PlaylistListResponse response = request.execute();
//
//        // 3. playlist 도메인에 저장하기
//        for (Playlist getPlaylist : response.getItems()) {
//            // 3.1 필요한 데이터만 추출해서 내 Playlist 엔티티에 매핑
//            Playlists playlist = new Playlists();
//            playlist.setPlaylistId(getPlaylist.getId());
//            playlist.setPlaylistTitle(getPlaylist.getSnippet().getTitle());
//            playlist.setServiceType("track&recover");
//            playlist.setUser(user);
//            // 3.2 Playlist 객체를 DB에 저장
//            playlistRepository.save(playlist);
//
//            // 4. 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
//            musicService.initiallyAddVideoDetails(getPlaylist.getId());
//        }
//    }
}
