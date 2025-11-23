package youtube.youtubeService.policy.simple;

import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.internal.MusicDetailsDto;
import youtube.youtubeService.policy.SearchPolicy;

public class SimpleSearchPolicy implements SearchPolicy {

    public String search(MusicDetailsDto musicToSearch) {
        return musicToSearch.videoTitle().concat("-").concat(musicToSearch.videoUploader());
    }
}
