package youtube.youtubeService.dto;

import com.google.api.services.youtube.model.PlaylistItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
public class QuotaPlaylistItemPageDto {
    private final List<PlaylistItem> allPlaylists;
    private final String nextPageToken;
}
