package youtube.youtubeService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.policy.gemini.GeminiSearchPolicy;
import youtube.youtubeService.policy.simple.SimpleSearchPolicy;

@Configuration
public class SearchConfig {

    @Bean
    public SearchPolicy simpleSearchQuery() {
        return new SimpleSearchPolicy();
    }

    @Bean
    public SearchPolicy geminiSearchQuery(RestClient restClient, SearchPolicy simpleSearchQuery) {
        return new GeminiSearchPolicy(restClient, simpleSearchQuery);
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