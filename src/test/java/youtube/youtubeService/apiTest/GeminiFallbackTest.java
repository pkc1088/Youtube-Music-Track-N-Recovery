package youtube.youtubeService.apiTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.policy.SearchPolicy;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
public class GeminiFallbackTest {

    @Autowired
    SearchPolicy geminiSearchQuery;

    @MockitoSpyBean
    SearchPolicy simpleSearchQuery;
    @MockitoBean
    RestClient restClient;
    @Mock
    RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    RestClient.RequestBodySpec requestBodySpec;
    @Mock
    RestClient.ResponseSpec responseSpec;

    private final MusicDetailsDto musicToSearch = new MusicDetailsDto(
            123L, "videoId123", "Kiss And Say GoodBye", "Manhattans", 200, "This is an American Group", "R&B SOUL", "PLNj4bt23RjfsajCmUzYQbvZp0v-M8PU8t"
    );

    @BeforeEach
    void setUp() {
        // restClient.post() -> uri(...) -> ... -> retrieve() -> body() 순서
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), (Object) any())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body((Class<Object>) any())).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini Server Error"));
//        when(simpleSearchQuery.search(any(MusicDetailsDto.class))).thenReturn("Simple Search Result: Kiss And Say GoodBye - Manhattans");
    }

    @Test
    @DisplayName("Gemini API Fail(Ex Occurs) => SimpleSearch Fallback Test")
    void geminiModelTest() {
        // Given
        // setUp 에서 완료

        // When
        // 실제 GeminiSearchPolicy의 search를 호출함 (내부적으로 RestClient 에러 발생 -> catch 블록 이동)
        String result = geminiSearchQuery.search(musicToSearch);

        // Then
        log.info("Result: {}", result);

        // 1. 결과값이 SimpleSearch가 리턴한 값인지 확인
        assertThat(result).isEqualTo("Kiss And Say GoodBye-Manhattans");

        // 2. 실제로 simpleSearchQuery.search()가 호출되었는지 검증 (verify)
        verify(simpleSearchQuery, times(1)).search(any(MusicDetailsDto.class));
    }
}
