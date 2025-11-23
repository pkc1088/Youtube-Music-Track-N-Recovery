package youtube.youtubeService.policy;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.internal.MusicDetailsDto;

public interface SearchPolicy {
    String search(MusicDetailsDto musicToSearch);
}
