package youtube.youtubeService.dto.internal;

import youtube.youtubeService.domain.Music;
import java.util.List;

public record RecoveryTaskDto(
        TaskType type,
        PlannedReplacementDto swapInfo,         // SWAP 일 경우
        Music replacementMusic,                 // INSERT 일 때 사용 (Edge Case 2용)
        List<PlannedOutboxDto> outboxActions    // 수행해야 할 Outbox 액션들 (ADD, DELETE)
) {
    public enum TaskType { RECOVERY, EXTRA_RECOVERY, DELETE_ONLY }
}