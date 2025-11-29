package youtube.youtubeService.dto.response;

import youtube.youtubeService.domain.ActionLog;
import java.util.List;


public record ActionLogResponseDto (
        String userId,
        List<ActionLog> logs
) {}