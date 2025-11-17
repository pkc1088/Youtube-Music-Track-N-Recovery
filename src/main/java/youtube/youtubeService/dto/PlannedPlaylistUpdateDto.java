package youtube.youtubeService.dto;

import com.google.api.services.youtube.model.Video;

import java.util.List;
import java.util.Map;


public record PlannedPlaylistUpdateDto(List<Video> videosToInsert, List<Long> videosToDelete,
                                       Map<String, List<String>> illegalVideos) {
}
