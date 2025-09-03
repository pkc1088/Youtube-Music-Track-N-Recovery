package youtube.youtubeService.domain.enums;

import lombok.Getter;

public enum QuotaType {
    PAGINATION(1),
    SINGLE_SEARCH(1),
    VIDEO_INSERT(50),
    VIDEO_DELETE(50),
    VIDEO_SEARCH(100);

    @Getter
    private final long cost;

    QuotaType(long cost) {
        this.cost = cost;
    }
}