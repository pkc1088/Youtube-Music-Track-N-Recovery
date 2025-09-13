package youtube.youtubeService.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Data
public class PlaylistRegisterRequestDto {
    private String userId;
    private String selectedPlaylistsJson;
    private List<String> deselectedPlaylistIds = new ArrayList<>();

    public List<PlaylistDto> getSelectedPlaylists(ObjectMapper objectMapper) {
        if (this.selectedPlaylistsJson == null || this.selectedPlaylistsJson.isBlank()) {
            log.info("[PlaylistRegisterRequestDto is empty]");
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(this.selectedPlaylistsJson, new TypeReference<List<PlaylistDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse selectedPlaylistsJson", e);
            return Collections.emptyList();
        }
    }
}

/** OG 0913
@Data
public class PlaylistRegisterRequestDto {
    private String userId;
    private List<String> selectedPlaylistIds = new ArrayList<>();
    private List<String> deselectedPlaylistIds = new ArrayList<>();
}*/
