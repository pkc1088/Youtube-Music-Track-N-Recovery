package youtube.youtubeService.policy.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.policy.SearchPolicy;

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
        System.err.println("Gemini Search Policy");
        String query = musicToSearch.getVideoTitle().concat("-").concat(musicToSearch.getVideoUploader())
                .concat(" 이 정보를 보고 '노래제목-가수' 형태로 알려줘 무조건 저 양식을 지켜서");
        return getCompletion(GEMINI_FLASH, query);
    }
}