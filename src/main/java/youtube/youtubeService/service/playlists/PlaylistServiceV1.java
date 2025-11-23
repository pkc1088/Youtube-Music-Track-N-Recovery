package youtube.youtubeService.service.playlists;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.model.Playlist;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.dto.internal.PlaylistDto;
import youtube.youtubeService.dto.request.PlaylistRegisterRequestDto;
import youtube.youtubeService.dto.response.PlaylistRegisterResponseDto;
import youtube.youtubeService.dto.response.UserRegisterPlaylistsResponseDto;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceV1 implements PlaylistService {

    private final UserService userService;
    private final PlaylistRepository playlistRepository;
    private final PlaylistRegistrationUnitService playlistRegistrationUnitService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Playlists> findAllPlaylistsByUserIds(List<String> userIds) {
        return playlistRepository.findAllPlaylistsByUserIds(userIds);
    }

    @Override
    @Transactional
    public void removePlaylistsFromDB(List<String> deselectedPlaylistIds) {
        if (deselectedPlaylistIds == null || deselectedPlaylistIds.isEmpty()) {
            return;
        }
        playlistRepository.deleteAllByPlaylistIdsIn(deselectedPlaylistIds);
        log.info("Deletion requested for {} playlists.", deselectedPlaylistIds.size());
    }

    private Users findUserOptimized(String userId, List<Playlists> fetchedPlaylists) {
        if (fetchedPlaylists == null || fetchedPlaylists.isEmpty()) {
            // (쿼리 2 - A) 등록된 플리가 없으면(신규 유저 등), User 를 별도 조회
            return userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
        } else {
            // (쿼리 2 - B) 등록된 플리가 있다면, 쿼리 1에서 JOIN FETCH 한 User 객체를 재사용 (추가 쿼리 없음)
            return fetchedPlaylists.get(0).getUser();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException {
        // 1. (쿼리 1) DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<Playlists> registeredPlaylists = playlistRepository.findAllByUserIdWithUserFetch(userId);
        List<String> registeredPlaylistIds = registeredPlaylists.stream().map(Playlists::getPlaylistId).toList();
        Users user = findUserOptimized(userId, registeredPlaylists);

        String channelId = user.getUserChannelId();
        // 2. API 로 사용자의 모든 플레이리스트를 가져옴
        List<Playlist> playlists = playlistRegistrationUnitService.fetchAllPlaylists(userId, channelId);
        return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
    }

    @Override
    @Transactional
    public PlaylistRegisterResponseDto registerPlaylists(PlaylistRegisterRequestDto request) {
        // 1. (쿼리1) DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<Playlists> registeredPlaylists = playlistRepository.findAllByUserIdWithUserFetch(request.getUserId());
        List<String> registeredPlaylistIds = registeredPlaylists.stream().map(Playlists::getPlaylistId).toList();
        Users user = findUserOptimized(request.getUserId(), registeredPlaylists);

        // 2. 중복된 플레이리스트는 제외하고 등록
        PlaylistRegisterResponseDto playlistRegisterResponseDto = null;
        List<PlaylistDto> newlySelectedPlaylistsDto = request.getSelectedPlaylists(objectMapper);
        List<PlaylistDto> newlySelectedPlaylists = newlySelectedPlaylistsDto.stream()
                .filter(dto -> dto != null && !dto.getId().isBlank())
                .filter(dto -> !registeredPlaylistIds.contains(dto.getId())).toList();

        if (!newlySelectedPlaylists.isEmpty()) {
            playlistRegisterResponseDto = registerSelectedPlaylists(user, newlySelectedPlaylists);  // 중복되지 않는 플레이리스트만 등록
        }

        if (request.getDeselectedPlaylistIds() != null && !request.getDeselectedPlaylistIds().isEmpty()) {
            removePlaylistsFromDB(request.getDeselectedPlaylistIds()); // 체크 해제된 플레이리스트 DB 에서 삭제
        }

        log.info("[Registering Playlists Is Completed]");

        return playlistRegisterResponseDto;
    }

    private PlaylistRegisterResponseDto registerSelectedPlaylists(Users user, List<PlaylistDto> selectedPlaylistsDto) {

        String countryCode = user.getCountryCode();
        int succeedPlaylistCount = 0;

        for (PlaylistDto dto : selectedPlaylistsDto) {
            log.info("[playlistServiceV1]: {}, {}", dto.getId(), dto.getTitle()); // 3.1 Playlist 객체를 DB에 저장

            try {
                playlistRegistrationUnitService.saveSinglePlaylist(dto, user.getUserId(), countryCode);
            } catch (QuotaExceededException ex) {
                log.warn("[Quota Exceeded During Registration]: {}", dto.getTitle());
                log.info("[Aborted] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
                return new PlaylistRegisterResponseDto(succeedPlaylistCount, selectedPlaylistsDto.size());
            }
            // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
            log.info("playlist({}) is added to DB", dto.getTitle());
            succeedPlaylistCount++;
        }

        log.info("[Completed] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());

        return new PlaylistRegisterResponseDto(succeedPlaylistCount, selectedPlaylistsDto.size());
    }

}
