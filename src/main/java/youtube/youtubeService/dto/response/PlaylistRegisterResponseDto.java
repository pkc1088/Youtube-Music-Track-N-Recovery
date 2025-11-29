package youtube.youtubeService.dto.response;

import java.io.Serial;
import java.io.Serializable;


public record PlaylistRegisterResponseDto(
        int succeedPlaylistCount,
        int selectedPlaylistsCount
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}