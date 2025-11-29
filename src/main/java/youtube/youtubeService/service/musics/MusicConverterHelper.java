package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import org.springframework.stereotype.Component;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import java.util.List;

@Component
public class MusicConverterHelper {

    public Music makeVideoToMusic(Video replacementVideo, Playlists playlist) {
        List<String> tags = replacementVideo.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        return new Music(
                replacementVideo.getId(),
                replacementVideo.getSnippet().getTitle(),
                replacementVideo.getSnippet().getChannelTitle(),
                replacementVideo.getSnippet().getDescription(),
                joinedTags,
                playlist
        );
    }
}
