package youtube.youtubeService.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PlaylistRegisterRequestDto {
    private String userId;
    private List<String> selectedPlaylistIds = new ArrayList<>();
    private List<String> deselectedPlaylistIds = new ArrayList<>();
}