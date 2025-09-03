package youtube.youtubeService.dto;

import com.google.api.services.youtube.model.Playlist;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class QuotaApiPlaylistsPageDto {
    private final List<Playlist> playlists;
    private final String nextPageToken;
}
