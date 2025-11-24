package youtube.youtubeService.policy.gemini;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.policy.SearchPolicy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class GeminiSearchPolicy implements SearchPolicy {

    @Value("${googleai.api.key}")
    private String apiKey;
    private final RestClient restClient;
    private final SearchPolicy simpleSearchQuery;

    private static final String PRIMARY_MODEL = "gemini-2.5-flash-lite";
    private static final Map<String, Double> FALLBACK_MODELS_RPM = Map.of(
            "gemini-2.5-flash", 10.0,
            "gemini-2.0-flash", 15.0
             //"gemini-2.0-flash-lite", 30.0
    );

    // 모든 모델의 RateLimiter 맵
    private final RateLimiter primaryLimiter;
    private final Map<String, RateLimiter> fallbackLimiters;
    // 예비 모델들만 라운드 로빈하기 위한 리스트
    private final AtomicInteger fallbackModelIndex = new AtomicInteger(0);
    private final List<String> weightedFallbackModels = new ArrayList<>();

    public GeminiSearchPolicy(RestClient restClient, SearchPolicy simpleSearchQuery) {
        Map<String, Double> allModelRpm = new HashMap<>(FALLBACK_MODELS_RPM);

        this.restClient = restClient;
        this.simpleSearchQuery = simpleSearchQuery;
        // 모든 모델의 RPM 을 합쳐서 RateLimiter 맵 생성: RPM -> RPS
        this.fallbackLimiters = allModelRpm.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> RateLimiter.create(entry.getValue() / 60.0)));
        this.primaryLimiter = RateLimiter.create(15.0 / 60.0);
    }

    @PostConstruct
    public void initWeighted() {
        FALLBACK_MODELS_RPM.forEach((model, rpm) -> {
            int weight = rpm.intValue();  // 10, 15, 30
            for (int i = 0; i < weight; i++) {
                weightedFallbackModels.add(model);
            }
        });

        Collections.shuffle(weightedFallbackModels); // too many request 같은 편향을 좀 줄이는 용도
    }

    @Override
    public String search(MusicDetailsDto musicToSearch) {

        String query = setPrompt(musicToSearch);
        String modelToUse;

        if (primaryLimiter.tryAcquire()) {

            modelToUse = PRIMARY_MODEL;
            log.info("[GEMINI PRIMARY model [{}]. Calling model...]", modelToUse);

        } else {

            int i = fallbackModelIndex.getAndIncrement();
            modelToUse = weightedFallbackModels.get(i % weightedFallbackModels.size());
            RateLimiter fallbackLimiter = fallbackLimiters.get(modelToUse);

            fallbackLimiter.acquire();
            log.info("[GEMINI FALLBACK model [{}]. Calling model...", modelToUse);
        }

        try {
            return getCompletion(modelToUse, query);

        } catch (RestClientResponseException e) {
            log.warn("[Gemini Overload/Error] Model {} failed (Status: {}). Returning Simple Query", modelToUse, e.getStatusCode());
            // 여기서는 할만큼 했으니 그냥 '제목-가수'로 넘겨주자
            return simpleSearchQuery.search(musicToSearch);
        }
    }

    public String setPrompt(MusicDetailsDto musicToSearch) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("다음 유튜브에 게시된 음악 영상 정보를 보고, 노래 제목과 가수를 '노래제목-가수' 형식으로 정확히 알려줘. 반드시 이 양식을 지켜야 해.\n\n");
        promptBuilder.append("영상 제목: ").append(musicToSearch.videoTitle()).append("\n");
        promptBuilder.append("업로더: ").append(musicToSearch.videoUploader()).append("\n");

        if (musicToSearch.videoTags() != null && !musicToSearch.videoTags().isBlank()) {
            promptBuilder.append("태그: ").append(musicToSearch.videoTags()).append("\n");
        }

        if (musicToSearch.videoDescription() != null && !musicToSearch.videoDescription().isBlank()) {
            promptBuilder.append("설명: ").append(musicToSearch.videoDescription()).append("\n");
        }

        promptBuilder.append("\n답변은 반드시 '노래 제목-가수' 형식으로만 해줘.");

        return promptBuilder.toString();
    }

    public String getCompletion(String model, String text) {

        GeminiRequest geminiRequest = new GeminiRequest(text);
        GeminiResponse response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .body(geminiRequest)
                .retrieve()
                .body(GeminiResponse.class);

        return response.getCandidates()
                .stream()
                .findFirst()
                .flatMap(candidate -> candidate.getContent().getParts()
                        .stream()
                        .findFirst()
                        .map(GeminiResponse.TextPart::getText))
                .orElse(null);
    }
}