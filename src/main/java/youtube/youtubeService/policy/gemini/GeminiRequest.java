package youtube.youtubeService.policy.gemini;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GeminiRequest {

    private List<Content> contents;

    public GeminiRequest(String text) {
        this.contents = List.of(new Content(List.of(new TextPart(text))));
    }

    @Getter
    @AllArgsConstructor
    private static class Content {
        private List<TextPart> parts;
    }

    @Getter
    @AllArgsConstructor
    private static class TextPart {
        public String text;
    }

}
