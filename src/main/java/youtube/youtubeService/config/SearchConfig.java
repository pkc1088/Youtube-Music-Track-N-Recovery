package youtube.youtubeService.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.policy.gemini.GeminiSearchPolicy;
import youtube.youtubeService.policy.simple.SimpleSearchPolicy;

@Configuration
public class SearchConfig {
    /** 이 방식 테스트 해보기
    @ Bean
    public Map< String, SearchPolicy> searchPolicyMap(
            @ Qualifier("simpleSearchQuery") SearchPolicy simpleSearchQuery,
            @ Qualifier("geminiSearchQuery") SearchPolicy geminiSearchQuery) {
        Map< String, SearchPolicy> policyMap = new HashMap<>();
        policyMap.put("simple", simpleSearchQuery);
        policyMap.put("gemini", geminiSearchQuery);
        return policyMap;
    }
     */
    @Bean
    @Qualifier("simpleSearchQuery")
    public SearchPolicy simpleSearchQuery() {
        return new SimpleSearchPolicy();
    }

    @Bean
    @Qualifier("geminiSearchQuery")
    public SearchPolicy geminiSearchQuery(RestClient restClient) {
        return new GeminiSearchPolicy(restClient);
    }

    @Bean
    public RestClient geminiRestClient(@Value("${gemini.baseurl}") String baseUrl, @Value("${googleai.api.key}") String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}