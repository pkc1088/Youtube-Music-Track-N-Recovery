package youtube.youtubeService.service.playlists;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.Video;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import youtube.youtubeService.api.YoutubeApiClient;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.domain.Playlists;
import youtube.youtubeService.domain.Users;
import youtube.youtubeService.domain.enums.QuotaType;
import youtube.youtubeService.dto.*;
import youtube.youtubeService.exception.QuotaExceededException;
import youtube.youtubeService.repository.playlists.PlaylistRepository;
import lombok.extern.slf4j.Slf4j;
import youtube.youtubeService.service.QuotaService;
import youtube.youtubeService.service.musics.MusicService;
import youtube.youtubeService.service.users.UserService;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlaylistServiceV1 implements PlaylistService {

    private final UserService userService;
    private final PlaylistRepository playlistRepository;
    private final MusicService musicService;
    private final PlaylistRegistrationUnitService playlistRegistrationUnitService;
    private final ObjectMapper objectMapper;

    @Override
    public List<Playlists> findAllPlaylistsByUserId(String userId){
        return playlistRepository.findAllPlaylistsByUserId(userId);
    }

    @Override
    public List<Playlists> findAllPlaylistsByUserIds(List<String> userIds) {
        return playlistRepository.findAllPlaylistsByUserIds(userIds);
    }

    @Override
    public void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds) {
        for (String deselectedPlaylist : deselectedPlaylistIds) {
            log.info("playlist({}) is deleted from DB", deselectedPlaylist);
            playlistRepository.deletePlaylistByPlaylistId(deselectedPlaylist);
        }
    }

    @Override
    public UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException {
        // 1. API로 사용자의 모든 플레이리스트를 가져옴
        Users user = userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
        String channelId = user.getUserChannelId();
        List<Playlist> playlists = playlistRegistrationUnitService.fetchAllPlaylists(userId, channelId);
        // 2. DB에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<String> registeredPlaylistIds = findAllPlaylistsByUserId(userId).stream().map(Playlists::getPlaylistId).toList();

        return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
    }

    @Override
    public PlaylistRegistrationResultDto registerPlaylists(PlaylistRegisterRequestDto request) {
        // 1. DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
        List<String> registeredPlaylistIds = findAllPlaylistsByUserId(request.getUserId()).stream().map(Playlists::getPlaylistId).toList();
        // 2. 중복된 플레이리스트는 제외하고 등록
        List<PlaylistDto> newlySelectedPlaylistsDto = request.getSelectedPlaylists(objectMapper);

        List<PlaylistDto> newlySelectedPlaylists = newlySelectedPlaylistsDto.stream()
                .filter(dto -> dto != null && !dto.getId().isBlank())
                .filter(dto -> !registeredPlaylistIds.contains(dto.getId()))
                .toList();

        PlaylistRegistrationResultDto dto = null;
        if (!newlySelectedPlaylists.isEmpty()) {
            dto = registerSelectedPlaylists(request.getUserId(), newlySelectedPlaylists);  // 중복되지 않는 플레이리스트만 등록
        }

        if (request.getDeselectedPlaylistIds() != null && !request.getDeselectedPlaylistIds().isEmpty()) {
            removePlaylistsFromDB(request.getUserId(), request.getDeselectedPlaylistIds()); // 체크 해제된 플레이리스트 DB에서 삭제
        }
    
        log.info("[Registering Playlists Is Completed]");
        return dto;
    }

    private PlaylistRegistrationResultDto registerSelectedPlaylists(String userId, List<PlaylistDto> selectedPlaylistsDto) {
        Users user = userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
        String countryCode = user.getCountryCode();

        // PlaylistDto 를 이용해서 중복 플리 fetch 호출 제거함
        int succeedPlaylistCount = 0;
        for (PlaylistDto dto : selectedPlaylistsDto) {
            log.info("[playlistServiceV1]: {}, {}", dto.getId(), dto.getTitle());
            Playlists playlist = new Playlists();
            playlist.setPlaylistId(dto.getId());
            playlist.setPlaylistTitle(dto.getTitle());
            playlist.setServiceType(Playlists.ServiceType.RECOVER);
            playlist.setUser(user);
            // 3.1 Playlist 객체를 DB에 저장
            try {
                playlistRegistrationUnitService.saveSinglePlaylist(playlist, userId, dto.getId(), countryCode);
            } catch (QuotaExceededException ex) {
                log.warn("[Quota Exceeded During Registration]: {}", playlist.getPlaylistTitle());
                log.info("[Aborted] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
                return new PlaylistRegistrationResultDto(succeedPlaylistCount, selectedPlaylistsDto.size());
            }
            // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
            log.info("playlist({}) is added to DB", dto.getTitle());
            succeedPlaylistCount++;
        }
        log.info("[Completed] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
        return new PlaylistRegistrationResultDto(succeedPlaylistCount, selectedPlaylistsDto.size());
    }

    @Override
    public Map<String, List<String>> updatePlaylist(String userId, String countryCode, Playlists playlist, List<MusicSummaryDto> pureDbMusicList) throws IOException {
        String playlistId = playlist.getPlaylistId();
        log.info("[update playlist start: {}]", playlistId);
        List<PlaylistItem> pureApiPlaylistItems;
        // 1. API 검색으로 고객 플레이리스트 아이템 목록 불러오기
        try {
            pureApiPlaylistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(userId, playlistId);
        } catch (IOException e) {
            removePlaylistsFromDB(userId, Collections.singletonList(playlistId));
            log.info("This playlist has been removed by the owner({})", playlistId);
            throw new IOException(e); // 할당량 1 소모한건 이미 try 에서 반영함
        }

        // 2. 고객 플레이리스트 아이템 담긴 디비 불러오기
        // List<Music> pureDbMusicList = musicService.findAllMusicByPlaylistId(playlistId);
        // 3. API 에서 video 상태 조회
        List<String> pureApiVideoIds = pureApiPlaylistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();

        VideoFilterResultPageDto videoFilterResult = playlistRegistrationUnitService.fetchAllVideos(userId, pureApiVideoIds, countryCode);
        List<Video> legalVideos = videoFilterResult.getLegalVideos();
        List<Video> unlistedCountryVideos = videoFilterResult.getUnlistedCountryVideos();

        // 4-1. 정상 비디오
        List<String> legalVideoIds = legalVideos.stream().map(Video::getId).toList();
        // 4-2. unlisted, 국가차단 비디오
        List<String> unlistedCountryVideoIds = unlistedCountryVideos.stream().map(Video::getId).toList();
        // 4-3. Delete / Private 비디오 (응답 자체가 안 온 videoId)
        List<String> privateDeletedVideoIds = pureApiVideoIds.stream().filter(videoId -> !legalVideoIds.contains(videoId) && !unlistedCountryVideoIds.contains(videoId)).toList();

        log.info("[legal] videos count : {}", legalVideoIds.size());
        log.info("[unlisted/country] video count : {}", unlistedCountryVideoIds.size());
        log.info("[deleted/private] video count : {}", privateDeletedVideoIds.size());
        // 5. 둘의 차이를 비교 → DB 반영
        Map<String, Long> apiCounts = pureApiVideoIds.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
//        Map<String, Long> dbCounts = pureDbMusicList.stream().collect(Collectors.groupingBy(Music::getVideoId, Collectors.counting()));
        Map<String, Long> dbCounts = pureDbMusicList.stream().collect(Collectors.groupingBy(MusicSummaryDto::videoId, Collectors.counting()));

        Set<String> allVideoIds = new HashSet<>(); // 영상 개수가 중요하진 x, 둘을 모두 순회하기 위해 담는 것일 뿐임
        allVideoIds.addAll(apiCounts.keySet());
        allVideoIds.addAll(dbCounts.keySet());

        for (String videoId : allVideoIds) {

            long apiCount = apiCounts.getOrDefault(videoId, 0L); //2
            long dbCount = dbCounts.getOrDefault(videoId, 0L); // 1

            long toInsertCount = apiCount - dbCount; // 1
            long toDeleteCount = dbCount - apiCount; // -1

            if (toInsertCount > 0 && legalVideoIds.contains(videoId)) { // toInsertCount 만큼 DBAddAction 을 반복
                legalVideos.stream().filter(v -> v.getId().equals(videoId)).findFirst()
                        .ifPresent(video -> IntStream.range(0, (int) toInsertCount).forEach(i -> musicService.saveSingleVideo(video, playlist)));
            }

            if (toDeleteCount > 0 && !unlistedCountryVideoIds.contains(videoId) && !privateDeletedVideoIds.contains(videoId)) { // 삭제할 개수만큼만 제한 후 Music 객체에서 ID만 추출 각 ID를 사용하여 삭제
//                pureDbMusicList.stream().filter(m -> m.getVideoId().equals(videoId)).limit(toDeleteCount)
//                      .map(Music::getId).forEach(musicService::deleteById);
                pureDbMusicList.stream().filter(m -> m.videoId().equals(videoId)).limit(toDeleteCount)
                        .map(MusicSummaryDto::id).forEach(musicService::deleteById);
            }
        }

        Map<String, List<String>> illegalVideos = new HashMap<>();

        for (PlaylistItem item : pureApiPlaylistItems) {
            String videoId = item.getSnippet().getResourceId().getVideoId();
            String playlistItemId = item.getId();

            if (unlistedCountryVideoIds.contains(videoId) || privateDeletedVideoIds.contains(videoId)) {
                illegalVideos.computeIfAbsent(videoId, k -> new ArrayList<>()).add(playlistItemId);
            }
        }

        log.info("[update playlist done: {}]", playlistId);
        return illegalVideos;
    }
}

/** OG CODE BEFORE 1104
     @Slf4j
     @Service
     @Transactional
     @RequiredArgsConstructor
     public class PlaylistServiceV1 implements PlaylistService {

     private final UserService userService;
     private final PlaylistRepository playlistRepository;
     private final MusicService musicService;
     private final PlaylistRegistrationUnitService playlistRegistrationUnitService;
     private final ObjectMapper objectMapper;

     //    public PlaylistServiceV1(UserService userService, PlaylistRepository playlistRepository,
     //                             MusicService musicService, PlaylistRegistrationUnitService playlistRegistrationUnitService,
     //                             ObjectMapper objectMapper) {
     //        this.userService = userService;
     //        this.playlistRepository = playlistRepository;
     //        this.musicService = musicService;
     //        this.playlistRegistrationUnitService = playlistRegistrationUnitService;
     //        this.objectMapper = objectMapper;
     //    }

     @Override
     public List<Playlists> findAllPlaylistsByUserId(String userId){
     return playlistRepository.findAllPlaylistsByUserId(userId);
     }

     @Override
     public void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds) {
     for (String deselectedPlaylist : deselectedPlaylistIds) {
     log.info("playlist({}) is deleted from DB", deselectedPlaylist);
     playlistRepository.deletePlaylistByPlaylistId(deselectedPlaylist);
     }
     }

     @Override
     public UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException {
     // 1. API로 사용자의 모든 플레이리스트를 가져옴
     Users user = userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
     String channelId = user.getUserChannelId();
     List<Playlist> playlists = playlistRegistrationUnitService.fetchAllPlaylists(userId, channelId);
     // 2. DB에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
     List<String> registeredPlaylistIds = findAllPlaylistsByUserId(userId).stream().map(Playlists::getPlaylistId).toList();

     return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
     }

     @Override
     public PlaylistRegistrationResultDto registerPlaylists(PlaylistRegisterRequestDto request) {
     // 1. DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
     List<String> registeredPlaylistIds = findAllPlaylistsByUserId(request.getUserId()).stream().map(Playlists::getPlaylistId).toList();
     // 2. 중복된 플레이리스트는 제외하고 등록
     List<PlaylistDto> newlySelectedPlaylistsDto = request.getSelectedPlaylists(objectMapper);

     List<PlaylistDto> newlySelectedPlaylists = newlySelectedPlaylistsDto.stream()
     .filter(dto -> dto != null && !dto.getId().isBlank())
     .filter(dto -> !registeredPlaylistIds.contains(dto.getId()))
     .toList();

     PlaylistRegistrationResultDto dto = null;
     if (!newlySelectedPlaylists.isEmpty()) {
     dto = registerSelectedPlaylists(request.getUserId(), newlySelectedPlaylists);  // 중복되지 않는 플레이리스트만 등록
     }

     if (request.getDeselectedPlaylistIds() != null && !request.getDeselectedPlaylistIds().isEmpty()) {
     removePlaylistsFromDB(request.getUserId(), request.getDeselectedPlaylistIds()); // 체크 해제된 플레이리스트 DB에서 삭제
     }

     log.info("[Registering Playlists Is Completed]");
     return dto;
     }

     private PlaylistRegistrationResultDto registerSelectedPlaylists(String userId, List<PlaylistDto> selectedPlaylistsDto) {
     Users user = userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
     String countryCode = user.getCountryCode();

     // PlaylistDto 를 이용해서 중복 플리 fetch 호출 제거함
     int succeedPlaylistCount = 0;
     for (PlaylistDto dto : selectedPlaylistsDto) {
     log.info("[playlistServiceV1]: {}, {}", dto.getId(), dto.getTitle());
     Playlists playlist = new Playlists();
     playlist.setPlaylistId(dto.getId());
     playlist.setPlaylistTitle(dto.getTitle());
     playlist.setServiceType(Playlists.ServiceType.RECOVER);
     playlist.setUser(user);
     // 3.1 Playlist 객체를 DB에 저장
     try {
     playlistRegistrationUnitService.saveSinglePlaylist(playlist, userId, dto.getId(), countryCode);
     } catch (QuotaExceededException ex) {
     log.warn("[Quota Exceeded During Registration]: {}", playlist.getPlaylistTitle());
     log.info("[Aborted] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
     return new PlaylistRegistrationResultDto(succeedPlaylistCount, selectedPlaylistsDto.size());
     }
     // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
     log.info("playlist({}) is added to DB", dto.getTitle());
     succeedPlaylistCount++;
     }
     log.info("[Completed] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
     return new PlaylistRegistrationResultDto(succeedPlaylistCount, selectedPlaylistsDto.size());
     }

     @Override
     public Map<String, List<String>> updatePlaylist(String userId, String countryCode, Playlists playlist) throws IOException {
     String playlistId = playlist.getPlaylistId();
     log.info("[update playlist start: {}]", playlistId);
     List<PlaylistItem> pureApiPlaylistItems;
     // 1. API 검색으로 고객 플레이리스트 아이템 목록 불러오기
     try {
     pureApiPlaylistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(userId, playlistId);
     } catch (IOException e) {
     removePlaylistsFromDB(userId, Collections.singletonList(playlistId));
     log.info("This playlist has been removed by the owner({})", playlistId);
     throw new IOException(e); // 할당량 1 소모한건 이미 try 에서 반영함
     }

     // 2. 고객 플레이리스트 아이템 담긴 디비 불러오기
     List<Music> pureDbMusicList = musicService.findAllMusicByPlaylistId(playlistId);
     // 3. API 에서 video 상태 조회
     List<String> pureApiVideoIds = pureApiPlaylistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();

     VideoFilterResultPageDto videoFilterResult = playlistRegistrationUnitService.fetchAllVideos(userId, pureApiVideoIds, countryCode);
     List<Video> legalVideos = videoFilterResult.getLegalVideos();
     List<Video> unlistedCountryVideos = videoFilterResult.getUnlistedCountryVideos();

     // 4-1. 정상 비디오
     List<String> legalVideoIds = legalVideos.stream().map(Video::getId).toList();
     // 4-2. unlisted, 국가차단 비디오
     List<String> unlistedCountryVideoIds = unlistedCountryVideos.stream().map(Video::getId).toList();
     // 4-3. Delete / Private 비디오 (응답 자체가 안 온 videoId)
     List<String> privateDeletedVideoIds = pureApiVideoIds.stream().filter(videoId -> !legalVideoIds.contains(videoId) && !unlistedCountryVideoIds.contains(videoId)).toList();

     log.info("[legal] videos count : {}", legalVideoIds.size());
     log.info("[unlisted/country] video count : {}", unlistedCountryVideoIds.size());
     log.info("[deleted/private] video count : {}", privateDeletedVideoIds.size());
     // 5. 둘의 차이를 비교 → DB 반영
     Map<String, Long> apiCounts = pureApiVideoIds.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
     Map<String, Long> dbCounts = pureDbMusicList.stream().collect(Collectors.groupingBy(Music::getVideoId, Collectors.counting()));

     Set<String> allVideoIds = new HashSet<>(); // 영상 개수가 중요하진 x, 둘을 모두 순회하기 위해 담는 것일 뿐임
     allVideoIds.addAll(apiCounts.keySet());
     allVideoIds.addAll(dbCounts.keySet());

     for (String videoId : allVideoIds) {

     long apiCount = apiCounts.getOrDefault(videoId, 0L); //2
     long dbCount = dbCounts.getOrDefault(videoId, 0L); // 1

     long toInsertCount = apiCount - dbCount; // 1
     long toDeleteCount = dbCount - apiCount; // -1

     if (toInsertCount > 0 && legalVideoIds.contains(videoId)) { // toInsertCount 만큼 DBAddAction 을 반복
     legalVideos.stream().filter(v -> v.getId().equals(videoId)).findFirst()
     .ifPresent(video -> IntStream.range(0, (int) toInsertCount).forEach(i -> musicService.saveSingleVideo(video, playlist)));
     }

     if (toDeleteCount > 0 && !unlistedCountryVideoIds.contains(videoId) && !privateDeletedVideoIds.contains(videoId)) { // 삭제할 개수만큼만 제한 후 Music 객체에서 ID만 추출 각 ID를 사용하여 삭제
     pureDbMusicList.stream().filter(m -> m.getVideoId().equals(videoId)).limit(toDeleteCount)
     .map(Music::getId).forEach(musicService::deleteById);
     }
     }

     Map<String, List<String>> illegalVideos = new HashMap<>();

     for (PlaylistItem item : pureApiPlaylistItems) {
     String videoId = item.getSnippet().getResourceId().getVideoId();
     String playlistItemId = item.getId();

     if (unlistedCountryVideoIds.contains(videoId) || privateDeletedVideoIds.contains(videoId)) {
     illegalVideos.computeIfAbsent(videoId, k -> new ArrayList<>()).add(playlistItemId);
     }
     }

     log.info("[update playlist done: {}]", playlistId);
     return illegalVideos;
     }
     }
 */

/** OGCODE BEFORE 1024
 @Slf4j
 @Service
 @Transactional
 public class PlaylistServiceV1 implements PlaylistService {

 private final UserService userService;
 private final PlaylistRepository playlistRepository;
 private final MusicService musicService;
 private final PlaylistRegistrationUnitService playlistRegistrationUnitService;
 private final ObjectMapper objectMapper;

 public PlaylistServiceV1(UserService userService, PlaylistRepository playlistRepository,
 MusicService musicService, PlaylistRegistrationUnitService playlistRegistrationUnitService,
 ObjectMapper objectMapper) {
 this.userService = userService;
 this.playlistRepository = playlistRepository;
 this.musicService = musicService;
 this.playlistRegistrationUnitService = playlistRegistrationUnitService;
 this.objectMapper = objectMapper;
 }

 @Override
 public List<Playlists> findAllPlaylistsByUserId(String userId){
 return playlistRepository.findAllPlaylistsByUserId(userId);
 }

 @Override
 public void removePlaylistsFromDB(String userId, List<String> deselectedPlaylistIds) {
 for (String deselectedPlaylist : deselectedPlaylistIds) {
 log.info("playlist({}) is deleted from DB", deselectedPlaylist);
 playlistRepository.deletePlaylistByPlaylistId(deselectedPlaylist);
 }
 }

 @Override
 public UserRegisterPlaylistsResponseDto userRegisterPlaylists(String userId) throws IOException {
 // 1. API로 사용자의 모든 플레이리스트를 가져옴
 Users user = userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
 String channelId = user.getUserChannelId();
 List<Playlist> playlists = playlistRegistrationUnitService.fetchAllPlaylists(userId, channelId);
 // 2. DB에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
 List<String> registeredPlaylistIds = findAllPlaylistsByUserId(userId).stream().map(Playlists::getPlaylistId).toList();

 return new UserRegisterPlaylistsResponseDto(userId, playlists, registeredPlaylistIds);
 }

 @Override
 public PlaylistRegistrationResultDto registerPlaylists(PlaylistRegisterRequestDto request) {
 // 1. DB 에서 사용자가 이미 등록한 플레이리스트 목록을 가져옴
 List<String> registeredPlaylistIds = findAllPlaylistsByUserId(request.getUserId()).stream().map(Playlists::getPlaylistId).toList();
 // 2. 중복된 플레이리스트는 제외하고 등록
 List<PlaylistDto> newlySelectedPlaylistsDto = request.getSelectedPlaylists(objectMapper);

 List<PlaylistDto> newlySelectedPlaylists = newlySelectedPlaylistsDto.stream()
 .filter(dto -> dto != null && !dto.getId().isBlank())
 .filter(dto -> !registeredPlaylistIds.contains(dto.getId()))
 .toList();

 PlaylistRegistrationResultDto dto = null;
 if (!newlySelectedPlaylists.isEmpty()) {
 dto = registerSelectedPlaylists(request.getUserId(), newlySelectedPlaylists);  // 중복되지 않는 플레이리스트만 등록
 }

 if (request.getDeselectedPlaylistIds() != null && !request.getDeselectedPlaylistIds().isEmpty()) {
 removePlaylistsFromDB(request.getUserId(), request.getDeselectedPlaylistIds()); // 체크 해제된 플레이리스트 DB에서 삭제
 }

 log.info("[Registering Playlists Is Completed]");
 return dto;
 }

 private PlaylistRegistrationResultDto registerSelectedPlaylists(String userId, List<PlaylistDto> selectedPlaylistsDto) {
 Users user = userService.getUserByUserId(userId).orElseThrow(() -> new IllegalArgumentException("[No User Found]"));
 String countryCode = user.getCountryCode();

 // PlaylistDto 를 이용해서 중복 플리 fetch 호출 제거함
 int succeedPlaylistCount = 0;
 for (PlaylistDto dto : selectedPlaylistsDto) {
 log.info("[playlistServiceV1]: {}, {}", dto.getId(), dto.getTitle());
 Playlists playlist = new Playlists();
 playlist.setPlaylistId(dto.getId());
 playlist.setPlaylistTitle(dto.getTitle());
 playlist.setServiceType(Playlists.ServiceType.RECOVER);
 playlist.setUser(user);
 // 3.1 Playlist 객체를 DB에 저장
 try {
 playlistRegistrationUnitService.saveSinglePlaylist(playlist, userId, dto.getId(), countryCode);
 } catch (QuotaExceededException ex) {
 log.warn("[Quota Exceeded During Registration]: {}", playlist.getPlaylistTitle());
 log.info("[Aborted] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
 return new PlaylistRegistrationResultDto(succeedPlaylistCount, selectedPlaylistsDto.size());
 }
 // 3.2 해당 플레이리스트에 딸린 모든 음악을 Music 도메인에 저장
 log.info("playlist({}) is added to DB", dto.getTitle());
 succeedPlaylistCount++;
 }
 log.info("[Completed] Registration Result: {}/{}", succeedPlaylistCount, selectedPlaylistsDto.size());
 return new PlaylistRegistrationResultDto(succeedPlaylistCount, selectedPlaylistsDto.size());
 }

 @Override
 public IllegalVideosAndPaginationDto updatePlaylist(String userId, String countryCode, Playlists playlist) throws IOException {
 String playlistId = playlist.getPlaylistId();
 log.info("[update playlist start: {}]", playlistId);
 List<PlaylistItem> pureApiPlaylistItems;
 // 1. API 검색으로 고객 플레이리스트 목록 불러오기
 try {
 pureApiPlaylistItems = playlistRegistrationUnitService.fetchAllPlaylistItems(userId, playlistId);
 } catch (IOException e) {
 removePlaylistsFromDB(userId, Collections.singletonList(playlistId));
 log.info("This playlist has been removed by the owner({})", playlistId);
 throw new IOException(e); // 할당량 1 소모한건 이미 try 에서 반영함
 }

 // 2. 고객 플레이리스트 아이템 담긴 디비 불러오기
 List<Music> pureDbMusicList = musicService.findAllMusicByPlaylistId(playlistId);
 // 3. API 에서 video 상태 조회
 List<String> pureApiVideoIds = pureApiPlaylistItems.stream().map(item -> item.getSnippet().getResourceId().getVideoId()).toList();

 long pagination = (long) Math.ceil((double) pureApiVideoIds.size() / 50);
 VideoFilterResultPageDto videoFilterResult = playlistRegistrationUnitService.fetchAllVideos(userId, pureApiVideoIds, countryCode);
 List<Video> legalVideos = videoFilterResult.getLegalVideos();
 List<Video> unlistedCountryVideos = videoFilterResult.getUnlistedCountryVideos();

 // 4-1. 정상 비디오
 List<String> legalVideoIds = legalVideos.stream().map(Video::getId).toList();
 // 4-2. unlisted, 국가차단 비디오
 List<String> unlistedCountryVideoIds = unlistedCountryVideos.stream().map(Video::getId).toList();
 // 4-3. Delete / Private 비디오 (응답 자체가 안 온 videoId)
 List<String> privateDeletedVideoIds = pureApiVideoIds.stream().filter(videoId -> !legalVideoIds.contains(videoId) && !unlistedCountryVideoIds.contains(videoId)).toList();

 log.info("[legal] videos count : {}", legalVideoIds.size());
 log.info("[unlisted/country] video count : {}", unlistedCountryVideoIds.size());
 log.info("[deleted/private] video count : {}", privateDeletedVideoIds.size());
 // 5. 둘의 차이를 비교 → DB 반영
 Map<String, Long> apiCounts = pureApiVideoIds.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
 Map<String, Long> dbCounts = pureDbMusicList.stream().collect(Collectors.groupingBy(Music::getVideoId, Collectors.counting()));

 Set<String> allVideoIds = new HashSet<>(); // 영상 개수가 중요하진 x, 둘을 모두 순회하기 위해 담는 것일 뿐임
 allVideoIds.addAll(apiCounts.keySet());
 allVideoIds.addAll(dbCounts.keySet());

 for (String videoId : allVideoIds) {

 long apiCount = apiCounts.getOrDefault(videoId, 0L); //2
 long dbCount = dbCounts.getOrDefault(videoId, 0L); // 1

 long toInsertCount = apiCount - dbCount; // 1
 long toDeleteCount = dbCount - apiCount; // -1

 if (toInsertCount > 0 && legalVideoIds.contains(videoId)) { // toInsertCount 만큼 DBAddAction 을 반복
 legalVideos.stream().filter(v -> v.getId().equals(videoId)).findFirst()
 .ifPresent(video -> IntStream.range(0, (int) toInsertCount).forEach(i -> musicService.saveSingleVideo(video, playlist)));
 }

 if (toDeleteCount > 0 && !unlistedCountryVideoIds.contains(videoId) && !privateDeletedVideoIds.contains(videoId)) { // 삭제할 개수만큼만 제한 후 Music 객체에서 ID만 추출 각 ID를 사용하여 삭제
 pureDbMusicList.stream().filter(m -> m.getVideoId().equals(videoId)).limit(toDeleteCount)
 .map(Music::getId).forEach(musicService::deleteById);
 }
 }

 Map<String, Integer> illegalVideoCounts = new HashMap<>();

 for (PlaylistItem item : pureApiPlaylistItems) {
 String videoId = item.getSnippet().getResourceId().getVideoId();
 if (unlistedCountryVideoIds.contains(videoId) || privateDeletedVideoIds.contains(videoId)) {
 illegalVideoCounts.put(videoId, illegalVideoCounts.getOrDefault(videoId, 0) + 1);
 }
 }

 log.info("[update playlist done: {}]", playlistId);
 return new IllegalVideosAndPaginationDto(illegalVideoCounts, (long) pagination);
 }
 }
 */