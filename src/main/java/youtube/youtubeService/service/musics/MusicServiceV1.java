package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.MusicSummaryDto;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.musics.MusicRepository;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@Transactional
public class MusicServiceV1 implements MusicService {

    private final MusicRepository musicRepository;
    private final SearchPolicy searchPolicy;
    private final YoutubeApiClient youtubeApiClient;
    private final JdbcTemplate jdbcTemplate;
    private final MusicConverterHelper musicConverterHelper;

    public MusicServiceV1(MusicRepository musicRepository, @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy, YoutubeApiClient youtubeApiClient,
                          JdbcTemplate jdbcTemplate, MusicConverterHelper musicConverterHelper) {
        this.musicRepository = musicRepository;
        this.searchPolicy = searchPolicy;
        this.youtubeApiClient = youtubeApiClient;
        this.jdbcTemplate = jdbcTemplate;
        this.musicConverterHelper = musicConverterHelper;
    }

    @Override
    public List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet) {
        return musicRepository.findAllMusicSummaryByPlaylistIds(playListsSet);
    }

    @Override
    public void deleteById(Long pk) {
        musicRepository.deleteById(pk);
    }

    @Override
    public void deleteAllByIdInBatch(List<Long> pks) {
        musicRepository.deleteAllByIdInBatch(pks);
    }

    @Override
    public List<Music> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId) {
        return musicRepository.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);
    }

    @Override
    public void updateMusicWithReplacement(long pk, Music replacementMusic) {
        musicRepository.updateMusicWithReplacement(pk, replacementMusic);
        //log.info("The music record update has been completed");
    }

    @Override
    public void upsertMusic(Music music) {
        musicRepository.upsertMusic(music);
    }

    @Override
    public void saveAllVideos(List<Video> legalVideos, Playlists playlist) {
        // 1. Video 리스트를 Music 리스트로 변환
        List<Music> musicsToSave = legalVideos.stream()
                .map(video -> musicConverterHelper.makeVideoToMusic(video, playlist))
                .collect(Collectors.toList());
        // 2. JdbcTemplate으로 bulk insert 실행
        bulkInsertMusic(musicsToSave);
    }

    // JdbcTemplate 을 사용하는 새 메서드
    @Override
    public void bulkInsertMusic(List<Music> musics) {
        String sql = "INSERT INTO music (video_id, video_title, video_uploader, video_description, video_tags, playlist_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Music music = musics.get(i);
                ps.setString(1, music.getVideoId());
                ps.setString(2, music.getVideoTitle());
                ps.setString(3, music.getVideoUploader());
                ps.setString(4, music.getVideoDescription());
                ps.setString(5, music.getVideoTags());
                ps.setString(6, music.getPlaylist().getPlaylistId());
            }

            @Override
            public int getBatchSize() {
                return musics.size();
            }
        });
    }

    @Override
    public Video searchVideoToReplace(Music musicToSearch) {

        String query = null;

        try {
            query = searchPolicy.search(musicToSearch);
            log.info("[searched with]: {}", query);

            /**
             * 이미 할당량 체크는 YoutubeService 에서 100 + 1 로 체크해줬음
             */
            SearchResult searchResult = youtubeApiClient.searchFromYoutube(query);

            if (searchResult == null) {
                log.warn("Search found no results for query [{}]. Returning placeholder.", query);
                return youtubeApiClient.createPlaceholderVideo("This video was supposed to be searched with [" + query + "]");
            }

            Video video = youtubeApiClient.fetchSingleVideo(searchResult.getId().getVideoId());
            log.info("[Found a music to replace]: {}, {}", video.getSnippet().getTitle(), video.getSnippet().getChannelTitle());

            return video;

        } catch (Exception e) {
            log.error("Video for a replacement Searching Error");
            return youtubeApiClient.createPlaceholderVideo("This video was supposed to be searched with [" + query + "]");
        }
        /**  Gemini model 에러는 이미 search 에서 처리함
            catch (RestClientResponseException geminiError) {
            // Gemini 4xx, 5xx 에러 (Overloaded 등)를 반환한 경우
            log.error("Gemini API Error (e.g., overloaded). Status: {}. Query: [{}]. Returning placeholder.", geminiError.getStatusCode(), query);
            return youtubeApiClient.createPlaceholderVideo("Gemini API is currently unavailable.");
        }*/


    }

    /*@Override
    public Music makeVideoToMusic(Video replacementVideo, Playlists playlist) {
        Music music = new Music();
        music.setVideoId(replacementVideo.getId());
        music.setVideoTitle(replacementVideo.getSnippet().getTitle());
        music.setVideoUploader(replacementVideo.getSnippet().getChannelTitle());
        music.setVideoDescription(replacementVideo.getSnippet().getDescription());
        List<String> tags = replacementVideo.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);
        music.setPlaylist(playlist);
        return music;
    }*/

}

//    @Override
//    public List<Music> findAllMusicByPlaylistId(String playlistId) {
//        return musicRepository.findAllMusicByPlaylistId(playlistId); 밑으로 대체
//    }
//    @Override
//    public List<Music> findAllWithPlaylistByPlaylistIn(List<Playlists> playListsSet) {
//        return musicRepository.findAllWithPlaylistByPlaylistIn(playListsSet); 이것도 밑으로 대체됨
//    }
//    @Override
//    public void saveSingleVideo(Video video, Playlists playlist) {
//        musicRepository.upsertMusic(makeVideoToMusic(video, playlist));
//    }
/** OG Before 251115

 @Slf4j
 @Service
 @Transactional
 public class MusicServiceV1 implements MusicService {

 private final MusicRepository musicRepository;
 private final SearchPolicy searchPolicy;
 private final YoutubeApiClient youtubeApiClient;
 private final JdbcTemplate jdbcTemplate;

 public MusicServiceV1(MusicRepository musicRepository,
 @Qualifier("geminiSearchQuery") SearchPolicy searchPolicy, YoutubeApiClient youtubeApiClient, JdbcTemplate jdbcTemplate) {
 this.musicRepository = musicRepository;
 this.searchPolicy = searchPolicy;
 this.youtubeApiClient = youtubeApiClient;
 this.jdbcTemplate = jdbcTemplate;
 }

 //    @Override
 //    public List<Music> findAllMusicByPlaylistId(String playlistId) {
 //        return musicRepository.findAllMusicByPlaylistId(playlistId); 밑으로 대체
 //    }
 //    @Override
 //    public List<Music> findAllWithPlaylistByPlaylistIn(List<Playlists> playListsSet) {
 //        return musicRepository.findAllWithPlaylistByPlaylistIn(playListsSet); 이것도 밑으로 대체됨
 //    }
 @Override
 public List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet) {
 return musicRepository.findAllMusicSummaryByPlaylistIds(playListsSet);
 }

 @Override
 public void deleteById(Long pk) {
 musicRepository.deleteById(pk);
 }

 @Override
 public List<Music> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId) {
 return musicRepository.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);
 }

 @Override
 public void updateMusicWithReplacement(long pk, Music replacementMusic) {
 musicRepository.updateMusicWithReplacement(pk, replacementMusic);
 log.info("The music record update has been completed");
 }

 @Override
 public void upsertMusic(Music music) {
 musicRepository.upsertMusic(music);
 }

 @Override
 public void saveSingleVideo(Video video, Playlists playlist) {
 musicRepository.upsertMusic(makeVideoToMusic(video, playlist));
 }

 @Override
 public void saveAllVideos(List<Video> legalVideos, Playlists playlist) {
 // 1. Video 리스트를 Music 리스트로 변환
 List<Music> musicsToSave = legalVideos.stream()
 .map(video -> makeVideoToMusic(video, playlist))
 .collect(Collectors.toList());
 // 2. JdbcTemplate으로 bulk insert 실행
 bulkInsertMusic(musicsToSave);
 }

 // JdbcTemplate 을 사용하는 새 메서드
 public void bulkInsertMusic(List<Music> musics) {
 String sql = "INSERT INTO music (video_id, video_title, video_uploader, video_description, video_tags, playlist_id) " +
 "VALUES (?, ?, ?, ?, ?, ?)";

 jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
 @Override
 public void setValues(PreparedStatement ps, int i) throws SQLException {
 Music music = musics.get(i);
 ps.setString(1, music.getVideoId());
 ps.setString(2, music.getVideoTitle());
 ps.setString(3, music.getVideoUploader());
 ps.setString(4, music.getVideoDescription());
 ps.setString(5, music.getVideoTags());
 ps.setString(6, music.getPlaylist().getPlaylistId());
 }

 @Override
 public int getBatchSize() {
 return musics.size();
 }
 });
 }

 @Override
 public Video searchVideoToReplace(Music musicToSearch) {

 String query = searchPolicy.search(musicToSearch); // Gemini Policy 사용
 log.info("[searched with]: {}", query);
 SearchResult searchResult;
 Video video;
 try {

            searchResult = youtubeApiClient.searchFromYoutube(query);
                    video = youtubeApiClient.fetchSingleVideo(searchResult.getId().getVideoId());
                    } catch (IOException e) {
                    log.error("Video for a replacement Searching Error");
                    return null;
                    }

                    log.info("[Found a music to replace]: {}, {}", video.getSnippet().getTitle(), video.getSnippet().getChannelTitle());
                    return video;
                    }

@Override
public Music makeVideoToMusic(Video replacementVideo, Playlists playlist) {
        Music music = new Music();
        music.setVideoId(replacementVideo.getId());
        music.setVideoTitle(replacementVideo.getSnippet().getTitle());
        music.setVideoUploader(replacementVideo.getSnippet().getChannelTitle());
        music.setVideoDescription(replacementVideo.getSnippet().getDescription());
        List<String> tags = replacementVideo.getSnippet().getTags();
        String joinedTags = (tags != null) ? String.join(",", tags) : null;
        music.setVideoTags(joinedTags);
        music.setPlaylist(playlist);
        return music;
        }

        }

 */