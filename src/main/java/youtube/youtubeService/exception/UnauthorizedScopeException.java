package youtube.youtubeService.exception;

import java.io.IOException;

public class UnauthorizedScopeException extends IOException {
    public UnauthorizedScopeException(String message) {
        super(message);
    }
}