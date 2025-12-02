package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.internal.MusicSummaryDto;
import youtube.youtubeService.dto.internal.VideoFilterResultPageDto;
import youtube.youtubeService.policy.SearchPolicy;
import youtube.youtubeService.repository.musics.MusicRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class MusicServiceImpl implements MusicService {

    private final MusicConverterHelper musicConverterHelper;
    private final YoutubeApiClient youtubeApiClient;
    private final VideoRecommender videoRecommender;
    private final MusicRepository musicRepository;
    private final SearchPolicy geminiSearchQuery;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<MusicSummaryDto> findAllMusicSummaryByPlaylistIds(List<Playlists> playListsSet) {
        return musicRepository.findAllMusicSummaryByPlaylistIds(playListsSet);
    }

    @Override
    @Transactional
    public void deleteAllByIdInBatch(List<Long> pks) {
        musicRepository.deleteAllByIdInBatch(pks);
    }

    @Override
    public List<MusicDetailsDto> getMusicListFromDBThruMusicId(String videoIdToDelete, String playlistId) {
        return musicRepository.getMusicListFromDBThruMusicId(videoIdToDelete, playlistId);
    }

    @Override
    @Transactional
    public void updateMusicWithReplacement(long pk, Music replacementMusic) {
        musicRepository.updateMusicWithReplacement(pk, replacementMusic);
    }

    @Override
    @Transactional
    public void upsertMusic(Music music) {
        musicRepository.upsertMusic(music);
    }

    @Override
    @Transactional
    public void saveAllVideos(List<Video> legalVideos, Playlists playlist) {
        // Video 리스트를 Music 리스트로 변환
        List<Music> musicsToSave = legalVideos.stream()
                .map(video -> musicConverterHelper.makeVideoToMusic(video, playlist))
                .collect(Collectors.toList());
        // JdbcTemplate으로 bulk insert 실행
        bulkInsertMusic(musicsToSave);
    }

    @Override
    @Transactional
    public void bulkInsertMusic(List<Music> musics) {
        String sql = "INSERT INTO music (video_id, video_title, video_uploader, video_duration, video_description, video_tags, playlist_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Music music = musics.get(i);
                ps.setString(1, music.getVideoId());
                ps.setString(2, music.getVideoTitle());
                ps.setString(3, music.getVideoUploader());
                ps.setInt(4, music.getVideoDuration());
                ps.setString(5, music.getVideoDescription());
                ps.setString(6, music.getVideoTags());
                ps.setString(7, music.getPlaylist().getPlaylistId());
            }

            @Override
            public int getBatchSize() {
                return musics.size();
            }
        });
    }

    @Override
    public Video searchVideoToReplace(MusicDetailsDto musicToSearch, String countryCode) {

        String query = null;

        try {
            query = geminiSearchQuery.search(musicToSearch);
            log.info("[searched with]: {}", query);

            // 이미 할당량 체크는 YoutubeService 에서 100 + 1 로 체크해줬음
            List<SearchResult> searchResults = youtubeApiClient.searchFromYoutube(query, countryCode);

            if (searchResults == null) {
                log.warn("Search found no results for query [{}]. Returning placeholder.", query);
                return youtubeApiClient.createPlaceholderVideo("This video was supposed to be searched with [" + query + "]");
            }

            // Video video = youtubeApiClient.fetchSingleVideo(searchResult.getId().getVideoId());
            List<String> videoIdsToSearch = searchResults.stream().map(result -> result.getId().getVideoId()).toList();
            VideoFilterResultPageDto videoDetails = youtubeApiClient.fetchVideoPage(videoIdsToSearch, countryCode);
            List<Video> candidatesVideos = videoDetails.legalVideos();

            Video video = videoRecommender.recommendBestMatch(musicToSearch, candidatesVideos);

            log.info("[Found a music to replace]: {}, {}", video.getSnippet().getTitle(), video.getSnippet().getChannelTitle());

            return video;

        } catch (Exception e) { // GEMINI Error 는 이미 search() 에서 처리함
            log.error("Video for a replacement Searching Error");
            return youtubeApiClient.createPlaceholderVideo("This video was supposed to be searched with [" + query + "]");
        }


    }

}