package youtube.youtubeService.policy.gemini;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class GeminiResponse {

    private List<Candidate> candidates;

    @Getter
    public static class Candidate {
        private Content content;
    }

    @Getter
    public static class Content {
        private List<TextPart> parts;
    }

    @Getter
    public static class TextPart {
        private String text;
    }

}
