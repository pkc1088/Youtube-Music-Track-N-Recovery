package youtube.youtubeService.exception.youtube;

import lombok.Getter;

@Getter
public class YoutubeApiException extends RuntimeException {

    private final int statusCode;

    public YoutubeApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}