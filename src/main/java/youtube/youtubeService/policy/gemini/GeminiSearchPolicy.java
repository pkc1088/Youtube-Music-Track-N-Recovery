package youtube.youtubeService.policy.gemini;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import youtube.youtubeService.domain.Music;
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

    public GeminiSearchPolicy(RestClient restClient) {
        this.restClient = restClient;

        Map<String, Double> allModelRpm = new HashMap<>(FALLBACK_MODELS_RPM);
        // 모든 모델의 RPM 을 합쳐서 RateLimiter 맵 생성: RPM -> RPS
        this.fallbackLimiters = allModelRpm.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> RateLimiter.create(entry.getValue() / 60.0)));
        this.primaryLimiter = RateLimiter.create(15.0 / 60.0);// allModelRpm.put(PRIMARY_MODEL, 15.0); // modelLimiters.get(PRIMARY_MODEL); 굳이?
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
            modelToUse = weightedFallbackModels.get(i % weightedFallbackModels.size()); //FALLBACK_MODEL_NAMES.get(fallbackModelIndex.getAndIncrement() % FALLBACK_MODEL_NAMES.size());
            RateLimiter fallbackLimiter = fallbackLimiters.get(modelToUse);

            fallbackLimiter.acquire();
            log.info("[GEMINI FALLBACK model [{}]. Calling model...", modelToUse);
        }

        try {
            return getCompletion(modelToUse, query);

        } catch (RestClientResponseException e) {
            log.warn("[Gemini Overload/Error] Model {} failed (Status: {}). Returning Simple Query", modelToUse, e.getStatusCode());
            /**
             * 여기서는 할만큼 했으니 그냥 '제목-가수'로 넘겨주자
             */
            return musicToSearch.videoTitle().concat(" - ").concat(musicToSearch.videoUploader()); // throw e; // RuntimeException 임
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

/** Primary + Fall Back Round-Robin (but not weighted)
 @Override
 public String search(Music musicToSearch) {

 String query = setPrompt(musicToSearch);
 String modelToUse;

 if (primaryLimiter.tryAcquire()) { // (성공) 주력 모델 사용 가능
 modelToUse = PRIMARY_MODEL;
 log.info("Permit acquired for PRIMARY model [{}]. Calling model...", modelToUse);

 } else {
 modelToUse = FALLBACK_MODEL_NAMES.get(fallbackModelIndex.getAndIncrement() % FALLBACK_MODEL_NAMES.size());
 RateLimiter fallbackLimiter = modelLimiters.get(modelToUse);

 fallbackLimiter.acquire();
 log.info("Permit acquired for FALLBACK model [{}]. Calling model...", modelToUse);
 }

 try {
 return getCompletion(modelToUse, query);
 } catch (RestClientResponseException e) {
 log.warn("[Gemini Overload/Error] Model {} failed (Status: {}). Returning null.", modelToUse, e.getStatusCode());
 throw e; // RuntimeException 임
 }
 }*/

/** Full Round Robin Work
 public class GeminiSearchPolicy implements SearchPolicy {

@Value("${googleai.api.key}")
private String apiKey;
private final RestClient restClient;

@SuppressWarnings("UnstableApiUsage")
private final RateLimiter rateLimiter = RateLimiter.create(1.0);
private final AtomicInteger modelIndex = new AtomicInteger(0);
private static final List<String> GEMINI_MODELS = List.of(
"gemini-2.5-flash-lite", // 15 RPM
"gemini-2.5-flash",      // 10 RPM
"gemini-2.0-flash",      // 15 RPM
"gemini-2.0-flash-lite"   // 30 RPM
);

public GeminiSearchPolicy(RestClient restClient) {
this.restClient = restClient;
}

@Override
@SuppressWarnings("UnstableApiUsage")
public String search(Music musicToSearch) {

String query = setPrompt(musicToSearch);

String modelToUse = GEMINI_MODELS.get(modelIndex.getAndIncrement() % GEMINI_MODELS.size());
log.info("Gemini Search Model [{}]. Waiting for Gemini rate limit permit...", modelToUse);
rateLimiter.acquire();
log.info("Permit acquired. Calling model: {}", modelToUse);

return getCompletion(modelToUse, query);
}

public String setPrompt(Music musicToSearch) {
StringBuilder promptBuilder = new StringBuilder();
promptBuilder.append("다음 유튜브에 게시된 음악 영상 정보를 보고, 노래 제목과 가수를 '노래제목-가수' 형식으로 정확히 알려줘. 반드시 이 양식을 지켜야 해.\n\n");
promptBuilder.append("영상 제목: ").append(musicToSearch.getVideoTitle()).append("\n");
promptBuilder.append("업로더: ").append(musicToSearch.getVideoUploader()).append("\n");

if (musicToSearch.getVideoTags() != null && !musicToSearch.getVideoTags().isBlank()) {
promptBuilder.append("태그: ").append(musicToSearch.getVideoTags()).append("\n");
}

if (musicToSearch.getVideoDescription() != null && !musicToSearch.getVideoDescription().isBlank()) {
promptBuilder.append("설명: ").append(musicToSearch.getVideoDescription()).append("\n");
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

*/

/** OGCODE BEOFRE 251114

 @Value("${googleai.api.key}")
 private String apiKey;
 private final RestClient restClient;

 //    public static final String GEMINI_2_5_FLASH_LITE = "gemini-2.5-flash-lite";
 //    public static final String GEMINI_2_5_FLASH = "gemini-2.5-flash";
 //    public static final String GEMINI_2_0_FLASH_LITE = "gemini-2.0-flash-lite";
 //    public static final String GEMINI_2_0_FLASH= "gemini-2.0-flash";

 public GeminiSearchPolicy(RestClient restClient) {
 this.restClient = restClient;
 }

 @Override
 public String search(Music musicToSearch) {

 log.info("searching technique : Gemini Search Policy");

 StringBuilder promptBuilder = new StringBuilder();
 promptBuilder.append("다음 유튜브에 게시된 음악 영상 정보를 보고, 노래 제목과 가수를 '노래제목-가수' 형식으로 정확히 알려줘. 반드시 이 양식을 지켜야 해.\n\n");
 promptBuilder.append("영상 제목: ").append(musicToSearch.getVideoTitle()).append("\n");
 promptBuilder.append("업로더: ").append(musicToSearch.getVideoUploader()).append("\n");

 if (musicToSearch.getVideoTags() != null && !musicToSearch.getVideoTags().isBlank()) {
 promptBuilder.append("태그: ").append(musicToSearch.getVideoTags()).append("\n");
 }

 if (musicToSearch.getVideoDescription() != null && !musicToSearch.getVideoDescription().isBlank()) {
 promptBuilder.append("설명: ").append(musicToSearch.getVideoDescription()).append("\n");
 }

 promptBuilder.append("\n답변은 반드시 '노래 제목-가수' 형식으로만 해줘.");
 String query = promptBuilder.toString();         // String query = musicToSearch.getVideoTitle().concat("-").concat(musicToSearch.getVideoUploader()).concat("이 정보를 보고 '노래제목-가수' 형태로 알려줘 무조건 저 양식을 지켜서");

 return getCompletion(GEMINI_2_5_FLASH_LITE, query);
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

*/