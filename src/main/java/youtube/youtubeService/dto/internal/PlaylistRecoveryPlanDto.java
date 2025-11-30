package youtube.youtubeService.dto.internal;

import com.google.api.services.youtube.model.Video;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.internal.PlannedOutboxDto;
import youtube.youtubeService.dto.internal.PlannedReplacementDto;

import java.util.List;

public record PlaylistRecoveryPlanDto(List<Video> toInsertVideoIds, List<Long> toDeleteVideoIds, List<Long> redundantEdgeDeleteIds, List<RecoveryTaskDto> tasks) {

    public boolean hasActions() {
        return !toInsertVideoIds.isEmpty() || !toDeleteVideoIds.isEmpty() || !redundantEdgeDeleteIds.isEmpty() || !tasks.isEmpty();
    }
}

