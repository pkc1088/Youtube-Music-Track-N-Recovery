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

//    interface Part {}
//    @Getter
//    @AllArgsConstructor
//    public static class InlineData {
//        private String mimeType;
//        private String data;
//    }

//    public GeminiRequest(String text) {
//        Part part = new TextPart(text);
//        Content content = new Content(Collections.singletonList(part));
//        this.contents = Arrays.asList(content);
//    }
//    @Getter
//    @AllArgsConstructor
//    private static class InlineDataPart implements Part {
//        public InlineData inlineData;
//    }
//    public GeminiRequest(String text, InlineData inlineData) {
//        this.contents = List.of(
//            new Content(
//                    List.of(
//                            new TextPart(text),
//                            new InlineDataPart(inlineData)
//                    )
//            )
//        );
//    }