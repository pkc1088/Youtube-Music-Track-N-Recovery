package youtube.youtubeService.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import youtube.youtubeService.service.QuotaService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    @PostMapping("/{userId}/set")
    @ResponseBody
    public void setUserQuota(@PathVariable String userId, @RequestParam long newUsage) {
        quotaService.setUserQuota(userId, newUsage);
    }

    @PostMapping("/{userId}/incr")
    @ResponseBody
    public void incrementUserQuota(@PathVariable String userId, @RequestParam long delta) {
        quotaService.incrementUserQuota(userId, delta);
    }

    @PostMapping("/{userId}/decr")
    @ResponseBody
    public void decrementUserQuota(@PathVariable String userId, @RequestParam long delta) {
        quotaService.decrementUserQuota(userId, delta);
    }

    @GetMapping("/getAllLegitKeys")
    @ResponseBody
    public String getAllLegitQuotas() {
        return quotaService.getAllLegitQuotas().toString();
    }
}
