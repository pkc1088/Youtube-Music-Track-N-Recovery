package youtube.youtubeService.service.playlists;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.dto.internal.FixedDataForRegistrationDto;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.musics.MusicService;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistPersistenceService {

    private final PlaylistRepository playlistRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MusicService musicService;


    @Transactional
    public void syncPlaylists(List<String> deselectedPlaylists, List<FixedDataForRegistrationDto> accumulatedFixedDataForRegistrationDto) {
        // 플레이리스트 및 음악 등록
        if (accumulatedFixedDataForRegistrationDto != null && !accumulatedFixedDataForRegistrationDto.isEmpty()) {
            // 모든 FixedData 에서 Playlist 객체만 뽑아서 리스트로 만듦
            List<Playlists> allPlaylists = accumulatedFixedDataForRegistrationDto.stream().map(FixedDataForRegistrationDto::playlist).toList();
            // 모든 FixedData 에서 Music 리스트를 뽑아서 하나의 거대한 리스트로 합침
            List<Music> allMusics = accumulatedFixedDataForRegistrationDto.stream().map(FixedDataForRegistrationDto::musicList).flatMap(List::stream).toList();

            bulkInsertPlaylists(allPlaylists);

            musicService.bulkInsertMusic(allMusics);

            log.info("Bulk Inserted: {} Playlists, {} Musics", allPlaylists.size(), allMusics.size());
        }

        // 해제한 플레이리스트 제거
        if (deselectedPlaylists != null && !deselectedPlaylists.isEmpty()) {
            removeDeselectedPlaylists(deselectedPlaylists);
        }

    }

    @Transactional
    public void bulkInsertPlaylists(List<Playlists> playlists) {
        String sql = "INSERT INTO playlists (playlist_id, playlist_title, service_type, last_checked_at, user_id) VALUES (?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Playlists p = playlists.get(i);
                ps.setString(1, p.getPlaylistId());
                ps.setString(2, p.getPlaylistTitle());
                ps.setString(3, p.getServiceType().name());
                ps.setTimestamp(4, Timestamp.valueOf(p.getLastCheckedAt()));
                ps.setString(5, p.getUser().getUserId());
            }

            @Override
            public int getBatchSize() {
                return playlists.size();
            }
        });
    }

    @Transactional
    public void removeDeselectedPlaylists(List<String> deselectedPlaylistIds) {
        if (deselectedPlaylistIds == null || deselectedPlaylistIds.isEmpty()) {
            return;
        }
        playlistRepository.deleteAllByPlaylistIdsIn(deselectedPlaylistIds);
    }
}
