package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import org.springframework.stereotype.Component;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import java.util.List;

@Component
public class MusicConverterHelper {

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
