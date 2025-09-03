package youtube.youtubeService.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class IllegalVideosAndPaginationDto {
    private final Map<String, Integer> illegalVideoIdWithCounts;
    private final long Pagination;
}
