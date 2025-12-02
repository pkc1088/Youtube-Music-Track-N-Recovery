//package youtube.youtubeService.dto.response;
//
//import com.google.api.services.youtube.model.PlaylistItem;
//import java.util.Collections;
//import java.util.List;
//
//public record PlaylistItemsFetchResponseDto (
//        boolean isModified,        // 변경 여부 (304면 false)
//        String newEtag,            // 변경되었다면 새로운 ETag
//        List<PlaylistItem> items   // 변경되었다면 전체 데이터 리스트
//) {
//    // 팩토리 메서드로 가독성 높이기
//    public static PlaylistItemsFetchResponseDto notModified() {
//        return new PlaylistItemsFetchResponseDto(false, null, Collections.emptyList());
//    }
//
//    public static PlaylistItemsFetchResponseDto modified(String newEtag, List<PlaylistItem> items) {
//        return new PlaylistItemsFetchResponseDto(true, newEtag, items);
//    }
//}
