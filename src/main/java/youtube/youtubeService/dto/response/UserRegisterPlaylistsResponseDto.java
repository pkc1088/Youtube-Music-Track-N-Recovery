package youtube.youtubeService.dto.response;

import com.google.api.services.youtube.model.Playlist;
import lombok.*;
import java.util.List;

@Data
public class UserRegisterPlaylistsResponseDto {
    private String userId;
    private List<Playlist> playlists;
    private List<String> registeredPlaylistIds;

    public UserRegisterPlaylistsResponseDto(String userId, List<Playlist> playlists, List<String> registeredPlaylistIds) {
        this.userId = userId;
        this.playlists = playlists;
        this.registeredPlaylistIds = registeredPlaylistIds;
    }
}
