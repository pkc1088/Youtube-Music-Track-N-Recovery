package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import org.springframework.stereotype.Component;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import java.util.List;

@Component
public class MusicConverterHelper {

    public Music makeVideoToMusic(Video replacementVideo, Playlists playlist) {

        int durationSeconds = parseDuration(replacementVideo.getContentDetails().getDuration());

        List<String> tags = replacementVideo.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;

        return new Music(
                replacementVideo.getId(),
                replacementVideo.getSnippet().getTitle(),
                replacementVideo.getSnippet().getChannelTitle(),
                durationSeconds,
                replacementVideo.getSnippet().getDescription(),
                joinedTags,
                playlist
        );
    }

    private Integer parseDuration(String isoDuration) {
        if (isoDuration == null || isoDuration.isEmpty()) {
            return 0;
        }
        try {
            return (int) java.time.Duration.parse(isoDuration).getSeconds();
        } catch (Exception e) {
            return 0;
        }
    }
}
