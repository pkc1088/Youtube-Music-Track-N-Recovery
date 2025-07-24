package youtube.youtubeService.dto;

import com.google.api.services.youtube.model.Video;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class VideoFilterResult {
    private final List<Video> legalVideos;
    private final List<Video> illegalVideos;
}