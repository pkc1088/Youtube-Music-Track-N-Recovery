package youtube.youtubeService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class PlaylistRegisterResponseDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private int succeedPlaylistCount;
    private int selectedPlaylistsCount;
}
