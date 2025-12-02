package youtube.youtubeService.exception.quota;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QuotaExceededException extends RuntimeException {

    // 내부 로직 실패용 (Redis 체크 실패)
    public QuotaExceededException(String message) {
        super(message);
    }

    // 외부 API 에러 감싸기용 (Stack Trace 보존)
    public QuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
