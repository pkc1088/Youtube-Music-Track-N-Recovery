package youtube.youtubeService.dto.internal;

import com.google.api.services.youtube.model.PlaylistItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public record QuotaPlaylistItemPageDto(List<PlaylistItem> allPlaylists, String nextPageToken) {
}
