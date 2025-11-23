package youtube.youtubeService.cqsTest;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
public class TestMetricsManager {
    private CountDownLatch latch;
    private final List<Long> apiExecutionTimes = new CopyOnWriteArrayList<>();
    private final List<Long> dbExecutionTimes = new CopyOnWriteArrayList<>();

    // 상세 메서드별 시간 저장소
    private final Map<String, List<Double>> detailedMetrics = new ConcurrentHashMap<>();

    // 상세 시간 기록
    public void recordDetailedTime(String methodName, double time) {
        detailedMetrics.computeIfAbsent(methodName, k -> new CopyOnWriteArrayList<>()).add(time);
    }

    public void printDetailedStats() {
        System.out.println("\n========== [Detailed Method Latency Report] ==========");
        detailedMetrics.forEach((method, times) -> {
            double avg = times.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double max = times.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double min = times.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            System.out.printf("> %-35s | Avg: %8.3f ms | Max: %8.3f ms | Min: %8.3f ms (Count: %d)%n", method, avg, max, min, times.size());
        });
        System.out.println("======================================================\n");
    }

    public void clearDetailedMetrics() {
        detailedMetrics.clear();
    }

    // 매 루프마다 Latch 초기화
    public void resetLatch(int expectedEvents) {
        this.latch = new CountDownLatch(expectedEvents);
        this.apiExecutionTimes.clear();
        this.dbExecutionTimes.clear();
    }

    // 메인 스레드 대기하는 곳
    public void await() throws InterruptedException {
        if (latch != null) {
            // 10초 타임아웃 (안전장치)
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Test Timeout: Async tasks did not finish in time.");
            }
        }
    }

    public void recordApiTime(long time) {
        apiExecutionTimes.add(time);
    }

    public void recordDbTime(long time) {
        dbExecutionTimes.add(time);
    }

    // 작업 하나(API+DB)가 완전히 끝났을 때 호출
    public void taskFinished() {
        if (latch != null) {
            latch.countDown();
        }
    }

    public long getTotalApiTime() {
        return apiExecutionTimes.stream().mapToLong(Long::longValue).sum();
    }

    public long getTotalDbTime() {
        return dbExecutionTimes.stream().mapToLong(Long::longValue).sum();
    }
}
