package youtube.youtubeService.dto.response;

import lombok.Data;
import youtube.youtubeService.domain.ActionLog;
import java.util.List;

@Data
public class ActionLogResponseDto {
    private String userId;
    private List<ActionLog> logs;

    public ActionLogResponseDto(String userId, List<ActionLog> logs) {
        this.userId = userId;
        this.logs = logs;
    }
}
