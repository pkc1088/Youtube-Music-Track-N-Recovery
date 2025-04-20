package youtube.youtubeService.policy;
import youtube.youtubeService.domain.Music;

public interface SearchPolicy {
    String search(Music musicToSearch);
}
