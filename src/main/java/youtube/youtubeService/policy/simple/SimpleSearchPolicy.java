package youtube.youtubeService.policy.simple;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.policy.SearchPolicy;

public class SimpleSearchPolicy implements SearchPolicy {

    public String search(Music musicToSearch) {
        return musicToSearch.getVideoTitle().concat("-").concat(musicToSearch.getVideoUploader());
    }
}
