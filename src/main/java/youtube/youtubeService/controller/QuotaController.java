package youtube.youtubeService.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import youtube.youtubeService.service.QuotaService;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/quota")
@PreAuthorize("hasRole('ADMIN')")
public class QuotaController {

    private final QuotaService quotaService;


    @GetMapping("/dashboard")
    public String dashboard() {
        return "quota-dashboard";
    }

    @ResponseBody
    @GetMapping("/all")
    public Map<String, Object> getAllQuotas() {
        return quotaService.getAllQuotas();
    }

    @ResponseBody
    @PostMapping("/limit")
    public void updateGlobalLimit(@RequestParam long newLimit) {
        quotaService.setGlobalLimit(newLimit);
    }

    @ResponseBody
    @PostMapping("/{userId}/set")
    public void setUserQuota(@PathVariable String userId, @RequestParam long newUsage) {
        quotaService.setUserQuota(userId, newUsage);
    }

    @ResponseBody
    @PostMapping("/{userId}/incr")
    public void incrementUserQuota(@PathVariable String userId, @RequestParam long delta) {
        quotaService.incrementUserQuota(userId, delta);
    }

    @ResponseBody
    @PostMapping("/{userId}/decr")
    public void decrementUserQuota(@PathVariable String userId, @RequestParam long delta) {
        quotaService.decrementUserQuota(userId, delta);
    }
}
