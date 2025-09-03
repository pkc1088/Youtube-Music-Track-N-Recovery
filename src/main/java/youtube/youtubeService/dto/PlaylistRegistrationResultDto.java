package youtube.youtubeService.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class PlaylistRegistrationResultDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private int succeedPlaylistCount;
    private int selectedPlaylistsCount;
}
