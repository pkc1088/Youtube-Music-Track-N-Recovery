package youtube.youtubeService.exception;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import youtube.youtubeService.exception.quota.QuotaExceededException;
import youtube.youtubeService.exception.users.UserQuitException;
import youtube.youtubeService.exception.users.UserRevokeException;
import youtube.youtubeService.exception.youtube.ChannelNotFoundException;
import youtube.youtubeService.exception.youtube.YoutubeApiException;
import youtube.youtubeService.exception.youtube.YoutubeNetworkException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(QuotaExceededException.class)
    public String handleQuotaExceededException(QuotaExceededException e) {
        log.warn("[QuotaExceededException] '{}'", e.getMessage());
        return "quota-exceeded";
    }

    @ExceptionHandler(UserRevokeException.class)
    public String handleUserRevokeException(UserRevokeException e, Model model) {
        log.warn("[UserRevokeException] '{}'", e.getMessage());
        model.addAttribute("errorMessage", "서비스 연결 상태가 좋지 않습니다. Google 보안 페이지에서 본 서비스 연동을 해지한 뒤 재가입해주세요.");
        return "error/default";
    }

    @ExceptionHandler(UserQuitException.class)
    public String handleUserQuitException(UserQuitException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[UserQuitException] '{}'", e.getMessage());
        model.addAttribute("errorMessage", "이미 탈퇴한 유저입니다.");

        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "error/default";
    }

    @ExceptionHandler(ChannelNotFoundException.class)
    public String handleChannelNotFoundException(ChannelNotFoundException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[ChannelNotFoundException] '{}'", e.getMessage());
        model.addAttribute("errorMessage", "유효하지 않는 채널입니다. 재가입해주세요.");

        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return "error/default";
    }

    @ExceptionHandler(YoutubeApiException.class)
    public String handleYoutubeApiException(YoutubeApiException e, Model model) {
        log.error("Youtube API Error: Status {}", e.getStatusCode(), e);
        model.addAttribute("errorMessage", "유튜브 서비스 처리 중 오류가 발생했습니다.");
        return "error/default";
    }

    @ExceptionHandler(YoutubeNetworkException.class)
    public String handleYoutubeNetworkException(YoutubeNetworkException e, Model model) {
        log.error("Youtube Network Error", e); // 스택 트레이스 기록
        model.addAttribute("errorMessage", "유튜브 연결 상태가 좋지 않습니다. 잠시 후 다시 시도해주세요.");
        return "error/default";
    }

}