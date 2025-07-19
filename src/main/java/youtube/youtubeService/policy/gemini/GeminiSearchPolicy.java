package youtube.youtubeService.policy.gemini;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.policy.SearchPolicy;

@Slf4j
public class GeminiSearchPolicy implements SearchPolicy {

    @Value("${googleai.api.key}")
    private String apiKey;
    private final RestClient restClient;

    public static final String GEMINI_FLASH = "gemini-1.5-flash";
    public static final String GEMINI_PRO = "gemini-pro";
    public static final String GEMINI_ULTIMATE = "gemini-ultimate";
    public static final String GEMINI_PRO_VISION = "gemini-pro-vision";

    public GeminiSearchPolicy(RestClient restClient) {
        this.restClient = restClient;
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
        promptBuilder.append("\n답변은 반드시 '노래제목-가수' 형식으로만 해줘.");
        String query = promptBuilder.toString();

//        String query = musicToSearch.getVideoTitle().concat("-").concat(musicToSearch.getVideoUploader())
//                .concat(" 이 정보를 보고 '노래제목-가수' 형태로 알려줘 무조건 저 양식을 지켜서");
        return getCompletion(GEMINI_FLASH, query);
    }
}