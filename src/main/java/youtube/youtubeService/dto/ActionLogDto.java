package youtube.youtubeService.dto;

import lombok.Data;
import youtube.youtubeService.domain.ActionLog;
import java.util.List;

@Data
public class ActionLogDto {
    private String userId;
    private List<ActionLog> logs;

    public ActionLogDto(String userId, List<ActionLog> logs) {
        this.userId = userId;
        this.logs = logs;
    }
}
