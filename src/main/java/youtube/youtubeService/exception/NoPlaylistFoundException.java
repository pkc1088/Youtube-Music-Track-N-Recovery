package youtube.youtubeService.exception;

import java.io.IOException;

public class NoPlaylistFoundException extends IOException {
    public NoPlaylistFoundException(String message) {
        super(message);
    }
}
