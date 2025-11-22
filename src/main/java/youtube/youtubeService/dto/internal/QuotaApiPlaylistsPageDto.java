package youtube.youtubeService.dto.internal;

import com.google.api.services.youtube.model.Playlist;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

public record QuotaApiPlaylistsPageDto(List<Playlist> playlists, String nextPageToken) {
}
