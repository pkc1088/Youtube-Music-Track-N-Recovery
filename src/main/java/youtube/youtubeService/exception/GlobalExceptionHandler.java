package youtube.youtubeService.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QuotaExceededException.class)
    public String handleQuotaExceededException(Model model) {
        // 모델에 메시지 실어서 뷰에서 표현 가능
        model.addAttribute("errorMessage", "오늘의 할당량을 모두 소진했습니다. 내일 다시 시도해주세요.");
        return "quota-exceeded"; // quota-exceeded.html (Thymeleaf 템플릿)
    }
}