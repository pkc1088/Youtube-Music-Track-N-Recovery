package youtube.youtubeService.dto.response;

import com.google.api.services.youtube.model.Playlist;
import java.util.List;


public record UserRegisterPlaylistsResponseDto(
        String userId,
        List<Playlist> playlists,
        List<String> registeredPlaylistIds
) {}
