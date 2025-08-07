package youtube.youtubeService.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import youtube.youtubeService.domain.ActionLog;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.repository.ActionLogRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ActionLogService {

    private final ActionLogRepository actionLogRepository;

    public List<ActionLog> findByUserIdOrderByCreatedAtDesc(String userId) {
        return actionLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void deleteByUserId(String userId) {
        actionLogRepository.deleteByUserId(userId);
    }

    public void actionLogSave(String userId, String playlistId, ActionLog.ActionType actionType, Music trgVid, Music srcVid) {
        // 영상 제목 및 업로드자도 저장하는게 보기 좋을 듯
        ActionLog log = new ActionLog();
        log.setUserId(userId);
        log.setPlaylistId(playlistId);
        log.setActionType(actionType);
        log.setTargetVideoId(trgVid.getVideoId());
        log.setTargetVideoTitle(trgVid.getVideoTitle());
        log.setSourceVideoId(srcVid.getVideoId());
        log.setSourceVideoTitle(srcVid.getVideoTitle());
        actionLogRepository.save(log);
    }

    public Optional<ActionLog> findTodayRecoverLog(ActionLog.ActionType actionType, String targetVideoId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay(); // 오늘 00:00:00
        return actionLogRepository.findTodayRecoverLog(actionType, targetVideoId, todayStart);
    }
}
