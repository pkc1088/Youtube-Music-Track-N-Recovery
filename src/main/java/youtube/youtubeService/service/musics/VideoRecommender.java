package youtube.youtubeService.service.musics;

import com.google.api.services.youtube.model.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import youtube.youtubeService.domain.Music;
import youtube.youtubeService.dto.internal.MusicDetailsDto;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VideoRecommender {

    private static final double TITLE_WEIGHT = 0.5;
    private static final double CHANNEL_TITLE_WEIGHT = 0.2;
    private static final double DURATION_WEIGHT = 0.15;
    private static final double STATISTICS_WEIGHT = 0.15;
    private static final double MAX_LOG_VIEW = Math.log10(10_000_000);
    private static final double MAX_LOG_LIKE = Math.log10(100_000);
    private static final double MAX_LOG_COMMENT = Math.log10(10_000);


    public Video recommendBestMatch(MusicDetailsDto original, List<Video> candidates) {

        double bestScore = -1;
        Video bestVideo = null;

        for (Video candidate : candidates) {
            double score = computeScore(original, candidate);
            log.info("Final Score: {} | Candidate: {}, Title: {}, Uploader: {}", score, candidate.getId(), candidate.getSnippet().getTitle(), candidate.getSnippet().getChannelTitle());
            if (score > bestScore) {
                bestScore = score;
                bestVideo = candidate;
            }
        }

        return bestVideo;
    }

    private double computeScore(MusicDetailsDto original, Video candidate) {

        double titleSimilarity  = computeTitleSimilarityJaccard(original.videoTitle(), candidate.getSnippet().getTitle());
        double channelSimilarity = computeTitleSimilarityJaccard(original.videoUploader(), candidate.getSnippet().getChannelTitle());
        double durationScore = computeDurationSimilarity(original.videoDuration(), candidate.getContentDetails().getDuration());
        double statisticsScore = computeStatisticsScore(candidate.getStatistics().getViewCount(), candidate.getStatistics().getLikeCount(), candidate.getStatistics().getCommentCount());
        log.info("[Candidate Score Breakdown] Title: {}, Channel: {}, Duration: {}, Stats: {}",titleSimilarity, channelSimilarity, durationScore, statisticsScore);

        return titleSimilarity * TITLE_WEIGHT + channelSimilarity * CHANNEL_TITLE_WEIGHT + durationScore * DURATION_WEIGHT + statisticsScore  * STATISTICS_WEIGHT;
    }

    private double computeDurationSimilarity(int originalSeconds, String candidateDuration) {
        try {
            int candidateSeconds = parseIso8601DurationToSeconds(candidateDuration);

            if (originalSeconds == 0 || candidateSeconds == 0) return 0.5;

            long diff = Math.abs(originalSeconds - candidateSeconds);
            long max = Math.max(originalSeconds, candidateSeconds);

            return 1.0 - (double) diff / max;

        } catch (Exception e) {
            return 0.0;
        }
    }

    private int parseIso8601DurationToSeconds(String duration) {
        if (duration == null || !duration.startsWith("PT")) return 0;

        try {
            return (int) Duration.parse(duration).getSeconds();
        } catch (Exception e) {
            return 0;
        }
    }

    private double computeStatisticsScore(BigInteger view, BigInteger like, BigInteger comment) {

        double vLog = getLogValue(view);
        double lLog = getLogValue(like);
        double cLog = getLogValue(comment);

        // 정규화 (기준값(MAX) 넘어가면 1.0으로 처리)
        double viewScore = Math.min(vLog / MAX_LOG_VIEW, 1.0);
        double likeScore = Math.min(lLog / MAX_LOG_LIKE, 1.0);
        double commentScore = Math.min(cLog / MAX_LOG_COMMENT, 1.0);

        return (viewScore * 0.6) + (likeScore * 0.3) + (commentScore * 0.1);
    }

    private double getLogValue(BigInteger n) {
        // 값이 0이면 점수 0.0 (예외방지), 1 이상이면 log10(n) 계산 (정상)
        if (n == null) return 0.0;

        double val = n.doubleValue();
        if (val <= 1.0) return 0.0;

        return Math.log10(val);
    }

    private double computeTitleSimilarityJaccard(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;

        Set<String> set1 = tokenize(s1);
        Set<String> set2 = tokenize(s2);

        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        // Jaccard 계산
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {

        if (text == null) return Collections.emptySet();

        String cleaned = text.toLowerCase()
                .replaceAll("\\(.*?\\)|\\[.*?\\]", "") // (Official), [MV] 제거
                .replaceAll("[^a-z0-9가-힣\\s]", "")    // 특수문자 제거
                .trim();

        if (cleaned.isEmpty()) return Collections.emptySet();

        // 중복 제거(HashSet)
        return new HashSet<>(Arrays.asList(cleaned.split("\\s+")));
    }




    public static double computeTitleSimilarityLeven(String a, String b) {
        if (a == null || b == null) return 0.0;
        a = a.toLowerCase();
        b = b.toLowerCase();

        int dist = levenshteinDistance(a, b);

        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;

        return 1.0 - ((double) dist / maxLen);
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }


}
