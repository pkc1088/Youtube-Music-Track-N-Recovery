package youtube.youtubeService.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.dto.response.ActionLogResponseDto;
import youtube.youtubeService.repository.ActionLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionLogService {

    private final ActionLogRepository actionLogRepository;

    // 엔티티 연관관계 x -> 아예 트잭 안줌
    public ActionLogResponseDto findByUserIdOrderByCreatedAtDesc(String userId) {
        return new ActionLogResponseDto(userId, actionLogRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional
    public void actionLogSave(String userId, String playlistId, ActionLog.ActionType actionType, MusicDetailsDto trgVid, Music srcVid) {
        ActionLog log = new ActionLog(userId, playlistId, actionType, trgVid.videoId(), trgVid.videoTitle(), srcVid.getVideoId(), srcVid.getVideoTitle());
        actionLogRepository.save(log);
    }

    // 엔티티 연관관계 x -> 아예 트잭 안줌
    public Optional<ActionLog> findTodayRecoverLog(ActionLog.ActionType actionType, String targetVideoId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay(); // 오늘 00:00:00
        return actionLogRepository.findTodayRecoverLog(actionType, targetVideoId, todayStart);
    }
}
