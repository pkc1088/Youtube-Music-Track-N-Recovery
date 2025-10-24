package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.musics.MusicRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@Transactional
public class MusicServiceV1 implements MusicService {

    private final MusicRepository musicRepository;
    private final SearchPolicy searchPolicy;
    private final YoutubeApiClient youtubeApiClient;


    public MusicServiceV1(MusicRepository musicRepository,
                          @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy, YoutubeApiClient youtubeApiClient) {
        this.musicRepository = musicRepository;
        this.searchPolicy = searchPolicy;
        this.youtubeApiClient = youtubeApiClient;
    }

    @Override
    public List<Music> findAllMusicByPlaylistId(String playlistId) {
        return musicRepository.findAllMusicByPlaylistId(playlistId);
    }

    @Override
    public void deleteById(Long pk) {
        musicRepository.deleteById(pk);
    }

    @Override
    public List<Music> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId) {
        return musicRepository.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);
    }

    @Override
    public void dBTrackAndRecoverPosition(String videoIdToDelete, Music replacementMusic, long pk) {
        musicRepository.dBTrackAndRecoverPosition(videoIdToDelete, replacementMusic, pk);
    }

    @Override
    public void upsertMusic(Music music) {
        musicRepository.upsertMusic(music);
    }

    @Override
    public void saveSingleVideo(Video video, Playlists playlist) {
        Music music = new Music();
        music.setVideoId(video.getId());
        music.setVideoTitle(video.getSnippet().getTitle());
        music.setVideoUploader(video.getSnippet().getChannelTitle());
        music.setVideoDescription(video.getSnippet().getDescription());
        List<String> tags = video.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);
        music.setPlaylist(playlist);

        musicRepository.upsertMusic(music);
    }

    @Override
    public void saveAllVideos(List<Video> legalVideos, Playlists playlist) {

        List<Music> musicsToSave = new ArrayList<>();

        for (Video video : legalVideos) {
            Music music = new Music();
            music.setVideoId(video.getId());
            music.setVideoTitle(video.getSnippet().getTitle());
            music.setVideoUploader(video.getSnippet().getChannelTitle());
            music.setVideoDescription(video.getSnippet().getDescription());
            List<String> tags = video.getSnippet().getTags();
            String joinedTags = (tags != null) ? String.join(",", tags) : null;
            music.setVideoTags(joinedTags);
            music.setPlaylist(playlist);

            musicsToSave.add(music);
        }
        musicRepository.saveAll(musicsToSave);
    }

    @Override
    public Video searchVideoToReplace(Music musicToSearch, Playlists playlist) {

        String query = searchPolicy.search(musicToSearch); // Gemini Policy 사용
        log.info("[searched with]: {}", query);
        SearchResult searchResult;
        Video video;
        try {
            /**
             * 이미 할당량 체크는 YoutubeService 에서 100 + 1 로 체크해줬음
             */
            searchResult = youtubeApiClient.searchFromYoutube(query);
            video = youtubeApiClient.fetchSingleVideo(searchResult.getId().getVideoId());
        } catch (IOException e) {
            log.error("Video for a replacement Searching Error");
            return null;
        }

        log.info("[Found a music to replace]: {}, {}", video.getSnippet().getTitle(), video.getSnippet().getChannelTitle());
        return video;
    }

    @Override
    public Music makeVideoToMusic(Video replacementVideo, Playlists playlist) {
        Music music = new Music();
        music.setVideoId(replacementVideo.getId());
        music.setVideoTitle(replacementVideo.getSnippet().getTitle());
        music.setVideoUploader(replacementVideo.getSnippet().getChannelTitle());
        music.setVideoDescription(replacementVideo.getSnippet().getDescription());
        List<String> tags = replacementVideo.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);
        music.setPlaylist(playlist);
        return music;
    }

}