package youtube.youtubeService.dto.internal;

import com.google.api.services.youtube.model.Video;

import java.util.List;
import java.util.Map;


public record PlannedPlaylistUpdateDto(List<Video> toInsertVideos, List<Long> toDeleteVideoIds, Map<String, List<String>> illegalVideos) {
}
