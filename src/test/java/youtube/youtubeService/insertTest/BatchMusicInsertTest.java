package youtube.youtubeService.insertTest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;

@Slf4j
public class BatchMusicInsertTest {

    private String brokenVideoId, title, uploader, description, tags, playlistId;

    @BeforeEach
    void setUp() {

        try {
            brokenVideoId = "XzEoBAltBII";
            title = "The Manhattans - Kiss And Say GoodBye";
            uploader = "Whistle_Missile";
            description = "just a test video";
            tags = "The Manhattams,R&B,Soul,7th album";
            playlistId = "PLNj4bt23RjfsajCmUzYQbvZp0v-M8PU8t";
        } catch (Exception e) {
        }
    }

    @Test
    void testBatchInsert() {

    }
}
