package youtube.youtubeService.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QuotaExceededException.class)
    public String handleQuotaExceededException() {
        return "quota-exceeded"; // quota-exceeded.html (Thymeleaf 템플릿)
    }
}