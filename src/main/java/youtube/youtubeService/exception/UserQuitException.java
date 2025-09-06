package youtube.youtubeService.exception;

import java.io.IOException;

public class UserQuitException extends IOException {
    public UserQuitException(String message) {
        super(message);
    }
}
