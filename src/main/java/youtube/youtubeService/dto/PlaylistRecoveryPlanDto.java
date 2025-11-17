package youtube.youtubeService.dto;

import com.google.api.services.youtube.model.Video;
import youtube.youtubeService.domain.Music;
import java.util.List;


public record PlaylistRecoveryPlanDto(List<Video> videosToInsert, List<Long> videosToDelete,
                                      List<PlannedOutboxDto> plannedOutboxList,
                                      List<PlannedReplacementDto> plannedReplacementDtoList, List<Music> edgeInsert,
                                      List<Long> edgeDelete) {

    public boolean hasActions() {
        return !videosToDelete.isEmpty() || !videosToInsert.isEmpty() || !plannedOutboxList.isEmpty() || !plannedReplacementDtoList.isEmpty() || !edgeInsert.isEmpty() || !edgeDelete.isEmpty();
    }
}
