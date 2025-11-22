package youtube.youtubeService.dto.internal;

import com.google.api.services.youtube.model.Video;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

public record VideoFilterResultPageDto(List<Video> legalVideos, List<Video> unlistedCountryVideos) {
}