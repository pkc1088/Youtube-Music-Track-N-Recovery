package youtube.youtubeService.dto.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import youtube.youtubeService.dto.internal.PlaylistDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Slf4j
public record PlaylistRegisterRequestDto(
        String userId,
        String selectedPlaylistsJson,
        List<String> deselectedPlaylistIds
) {

    private static final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PlaylistRegisterRequestDto {
        if (deselectedPlaylistIds == null) {
            deselectedPlaylistIds = new ArrayList<>();
        }
    }

    public List<PlaylistDto> getSelectedPlaylists() {
        if (this.selectedPlaylistsJson == null || this.selectedPlaylistsJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return mapper.readValue(this.selectedPlaylistsJson, new TypeReference<List<PlaylistDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse selectedPlaylistsJson", e);
            return Collections.emptyList();
        }
    }
}

