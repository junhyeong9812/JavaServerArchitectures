package server.benchmark;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 메모리 사용량 측정 및 분석 도구
 *
 * 측정 항목:
 * 1. Heap 메모리 사용량
 * 2. Non-Heap 메모리 사용량
 * 3. 메모리 풀별 상세 정보
 * 4. GC 활동 모니터링
 * 5. 메모리 누수 감지
 * 6. 메모리 압박 상황 분석
 */
public class MemoryProfiler {

    private static final Logger logger = LoggerFactory.getLogger(MemoryProfiler.class);

    // JMX Beans
    private final MemoryMXBean memoryBean;
    private final List<MemoryPoolMXBean> memoryPools;
    private final List<GarbageCollectorMXBean> gcBeans;

    // 모니터링 제어
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final List<MemorySnapshot> snapshots;
    private ScheduledFuture<?> monitoringTask;

    // 설정
    private volatile int samplingIntervalMs = 500;
    private volatile boolean enableDetailedPoolAnalysis = true;
    private volatile boolean enableGCAnalysis = true;

    public MemoryProfiler() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.memoryPools = ManagementFactory.getMemoryPoolMXBeans();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MemoryProfiler");
            t.setDaemon(true);
            return t;
        });

        this.snapshots = Collections.synchronizedList(new ArrayList<>());

        logger.info("MemoryProfiler initialized - {} memory pools, {} GC collectors",
                memoryPools.size(), gcBeans.size());
    }

    /**
     * 메모리 모니터링 시작
     */
    public void startMonitoring() {
        if (!monitoring.compareAndSet(false, true)) {
            logger.warn("Memory monitoring already started");
            return;
        }

        logger.info("Starting memory monitoring (interval: {}ms)", samplingIntervalMs);

        // 초기 스냅샷
        snapshots.clear();
        takeSnapshot();

        // 주기적 모니터링 시작
        monitoringTask = scheduler.scheduleAtFixedRate(
                this::takeSnapshot,
                samplingIntervalMs,
                samplingIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 메모리 모니터링 중지
     */
    public void stopMonitoring() {
        if (!monitoring.compareAndSet(true, false)) {
            logger.warn("Memory monitoring not started");
            return;
        }

        if (monitoringTask != null) {
            monitoringTask.cancel(false);
        }

        // 최종 스냅샷
        takeSnapshot();

        logger.info("Memory monitoring stopped - total snapshots: {}", snapshots.size());
    }

    /**
     * 메모리 스냅샷 수집
     */
    private void takeSnapshot() {
        try {
            long timestamp = System.currentTimeMillis();

            // 전체 메모리 정보
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            // Runtime 메모리 정보
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usedMemory = totalMemory - freeMemory;

            // 메모리 풀별 상세 정보
            Map<String, MemoryPoolInfo> poolInfos = new HashMap<>();
            if (enableDetailedPoolAnalysis) {
                for (MemoryPoolMXBean pool : memoryPools) {
                    MemoryUsage usage = pool.getUsage();
                    if (usage != null) {
                        poolInfos.put(pool.getName(), new MemoryPoolInfo(
                                pool.getName(),
                                pool.getType(),
                                usage.getUsed(),
                                usage.getCommitted(),
                                usage.getMax(),
                                pool.getPeakUsage() != null ? pool.getPeakUsage().getUsed() : -1
                        ));
                    }
                }
            }

            // GC 정보
            Map<String, GCInfo> gcInfos = new HashMap<>();
            if (enableGCAnalysis) {
                for (GarbageCollectorMXBean gcBean : gcBeans) {
                    gcInfos.put(gcBean.getName(), new GCInfo(
                            gcBean.getName(),
                            gcBean.getCollectionCount(),
                            gcBean.getCollectionTime()
                    ));
                }
            }

            MemorySnapshot snapshot = new MemorySnapshot(
                    timestamp,
                    heapUsage.getUsed(),
                    heapUsage.getCommitted(),
                    heapUsage.getMax(),
                    nonHeapUsage.getUsed(),
                    nonHeapUsage.getCommitted(),
                    nonHeapUsage.getMax(),
                    totalMemory,
                    freeMemory,
                    maxMemory,
                    usedMemory,
                    poolInfos,
                    gcInfos
            );

            snapshots.add(snapshot);

            logger.debug("Memory snapshot - heap: {}MB/{MB, pools: {}, GC collections: {}",
                    heapUsage.getUsed() / (1024 * 1024),
                    heapUsage.getMax() / (1024 * 1024),
                    poolInfos.size(),
                    gcInfos.values().stream().mapToLong(gc -> gc.collectionCount).sum());

        } catch (Exception e) {
            logger.error("Error taking memory snapshot", e);
        }
    }

    /**
     * 메모리 분석 결과 생성
     */
    public MemoryAnalysis analyze() {
        if (snapshots.isEmpty()) {
            return MemoryAnalysis.empty();
        }

        List<MemorySnapshot> sortedSnapshots = new ArrayList<>(snapshots);
        sortedSnapshots.sort(Comparator.comparing(MemorySnapshot::getTimestamp));

        MemorySnapshot first = sortedSnapshots.get(0);
        MemorySnapshot last = sortedSnapshots.get(sortedSnapshots.size() - 1);

        // 기본 통계
        long durationMs = last.getTimestamp() - first.getTimestamp();
        long heapIncrease = last.getHeapUsed() - first.getHeapUsed();
        long nonHeapIncrease = last.getNonHeapUsed() - first.getNonHeapUsed();

        // 최대값들
        long maxHeapUsed = sortedSnapshots.stream()
                .mapToLong(MemorySnapshot::getHeapUsed)
                .max().orElse(0);

        long maxNonHeapUsed = sortedSnapshots.stream()
                .mapToLong(MemorySnapshot::getNonHeapUsed)
                .max().orElse(0);

        // 평균 사용량
        double avgHeapUsed = sortedSnapshots.stream()
                .mapToLong(MemorySnapshot::getHeapUsed)
                .average().orElse(0);

        double avgNonHeapUsed = sortedSnapshots.stream()
                .mapToLong(MemorySnapshot::getNonHeapUsed)
                .average().orElse(0);

        // GC 분석
        GCAnalysis gcAnalysis = analyzeGC(sortedSnapshots);

        // 메모리 누수 감지
        LeakDetectionResult leakDetection = detectMemoryLeaks(sortedSnapshots);

        // 메모리 압박 분석
        MemoryPressureAnalysis pressureAnalysis = analyzeMemoryPressure(sortedSnapshots);

        return new MemoryAnalysis(
                first.getTimestamp(),
                last.getTimestamp(),
                durationMs,
                snapshots.size(),
                heapIncrease,
                nonHeapIncrease,
                maxHeapUsed,
                maxNonHeapUsed,
                avgHeapUsed,
                avgNonHeapUsed,
                gcAnalysis,
                leakDetection,
                pressureAnalysis
        );
    }

    /**
     * GC 활동 분석
     */
    private GCAnalysis analyzeGC(List<MemorySnapshot> snapshots) {
        if (snapshots.size() < 2) {
            return new GCAnalysis(0, 0, 0, new HashMap<>());
        }

        MemorySnapshot first = snapshots.get(0);
        MemorySnapshot last = snapshots.get(snapshots.size() - 1);

        long totalGcCount = 0;
        long totalGcTime = 0;
        Map<String, Long> gcCountsByCollector = new HashMap<>();

        for (String gcName : first.getGcInfos().keySet()) {
            GCInfo firstGc = first.getGcInfos().get(gcName);
            GCInfo lastGc = last.getGcInfos().get(gcName);

            if (firstGc != null && lastGc != null) {
                long gcCount = lastGc.collectionCount - firstGc.collectionCount;
                long gcTime = lastGc.collectionTime - firstGc.collectionTime;

                totalGcCount += gcCount;
                totalGcTime += gcTime;
                gcCountsByCollector.put(gcName, gcCount);
            }
        }

        long durationMs = last.getTimestamp() - first.getTimestamp();
        double gcOverhead = durationMs > 0 ? (double) totalGcTime / durationMs * 100 : 0;

        return new GCAnalysis(totalGcCount, totalGcTime, gcOverhead, gcCountsByCollector);
    }

    /**
     * 메모리 누수 감지
     */
    private LeakDetectionResult detectMemoryLeaks(List<MemorySnapshot> snapshots) {
        if (snapshots.size() < 10) {
            return new LeakDetectionResult(false, 0, "Insufficient data");
        }

        // 지속적인 메모리 증가 패턴 감지
        List<Long> heapUsages = snapshots.stream()
                .map(MemorySnapshot::getHeapUsed)
                .toList();

        // 선형 회귀로 증가 추세 계산
        double trend = calculateLinearTrend(heapUsages);

        // 메모리 증가율이 1MB/min 이상이면 누수 의심
        boolean leakSuspected = trend > (1024 * 1024 / 60.0); // 1MB per minute

        String analysis = leakSuspected ?
                String.format("Continuous memory growth detected: %.2f MB/min", trend / (1024 * 1024) * 60) :
                "No significant memory growth pattern detected";

        return new LeakDetectionResult(leakSuspected, trend, analysis);
    }

    /**
     * 메모리 압박 상황 분석
     */
    private MemoryPressureAnalysis analyzeMemoryPressure(List<MemorySnapshot> snapshots) {
        long maxHeap = snapshots.stream()
                .mapToLong(s -> s.getHeapMax())
                .filter(max -> max > 0)
                .findFirst().orElse(0);

        if (maxHeap <= 0) {
            return new MemoryPressureAnalysis(0, 0, false, "Unable to determine heap limit");
        }

        // 최대 사용률 계산
        double maxUtilization = snapshots.stream()
                .mapToDouble(s -> (double) s.getHeapUsed() / maxHeap * 100)
                .max().orElse(0);

        // 평균 사용률 계산
        double avgUtilization = snapshots.stream()
                .mapToDouble(s -> (double) s.getHeapUsed() / maxHeap * 100)
                .average().orElse(0);

        // 압박 상황 판정 (80% 이상 사용)
        boolean underPressure = maxUtilization > 80.0;

        String analysis = String.format(
                "Max utilization: %.1f%%, Avg utilization: %.1f%%, Pressure: %s",
                maxUtilization, avgUtilization, underPressure ? "HIGH" : "NORMAL");

        return new MemoryPressureAnalysis(maxUtilization, avgUtilization, underPressure, analysis);
    }

    /**
     * 선형 추세 계산 (최소제곱법)
     */
    private double calculateLinearTrend(List<Long> values) {
        int n = values.size();
        if (n < 2) return 0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i; // 시간 인덱스
            double y = values.get(i); // 메모리 사용량

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // 기울기 계산 (slope)
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    /**
     * 현재 메모리 상태 스냅샷
     */
    public MemorySnapshot getCurrentSnapshot() {
        takeSnapshot();
        return snapshots.get(snapshots.size() - 1);
    }

    /**
     * 설정 메서드들
     */
    public void setSamplingInterval(int intervalMs) {
        this.samplingIntervalMs = intervalMs;
    }

    public void setEnableDetailedPoolAnalysis(boolean enable) {
        this.enableDetailedPoolAnalysis = enable;
    }

    public void setEnableGCAnalysis(boolean enable) {
        this.enableGCAnalysis = enable;
    }

    /**
     * 스냅샷 목록 (읽기 전용)
     */
    public List<MemorySnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    /**
     * 모니터링 상태
     */
    public boolean isMonitoring() {
        return monitoring.get();
    }

    /**
     * 리소스 정리
     */
    public void shutdown() {
        stopMonitoring();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // === 데이터 클래스들 ===

    /**
     * 메모리 스냅샷
     */
    public static class MemorySnapshot {
        private final long timestamp;
        private final long heapUsed, heapCommitted, heapMax;
        private final long nonHeapUsed, nonHeapCommitted, nonHeapMax;
        private final long totalMemory, freeMemory, maxMemory, usedMemory;
        private final Map<String, MemoryPoolInfo> poolInfos;
        private final Map<String, GCInfo> gcInfos;

        public MemorySnapshot(long timestamp, long heapUsed, long heapCommitted, long heapMax,
                              long nonHeapUsed, long nonHeapCommitted, long nonHeapMax,
                              long totalMemory, long freeMemory, long maxMemory, long usedMemory,
                              Map<String, MemoryPoolInfo> poolInfos, Map<String, GCInfo> gcInfos) {
            this.timestamp = timestamp;
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapCommitted = nonHeapCommitted;
            this.nonHeapMax = nonHeapMax;
            this.totalMemory = totalMemory;
            this.freeMemory = freeMemory;
            this.maxMemory = maxMemory;
            this.usedMemory = usedMemory;
            this.poolInfos = new HashMap<>(poolInfos);
            this.gcInfos = new HashMap<>(gcInfos);
        }

        // Getters
        public long getTimestamp() { return timestamp; }
        public long getHeapUsed() { return heapUsed; }
        public long getHeapCommitted() { return heapCommitted; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapCommitted() { return nonHeapCommitted; }
        public long getNonHeapMax() { return nonHeapMax; }
        public long getTotalMemory() { return totalMemory; }
        public long getFreeMemory() { return freeMemory; }
        public long getMaxMemory() { return maxMemory; }
        public long getUsedMemory() { return usedMemory; }
        public Map<String, MemoryPoolInfo> getPoolInfos() { return new HashMap<>(poolInfos); }
        public Map<String, GCInfo> getGcInfos() { return new HashMap<>(gcInfos); }
    }

    public static class MemoryPoolInfo {
        private final String name;
        private final MemoryType type;
        private final long used, committed, max, peak;

        public MemoryPoolInfo(String name, MemoryType type, long used, long committed, long max, long peak) {
            this.name = name;
            this.type = type;
            this.used = used;
            this.committed = committed;
            this.max = max;
            this.peak = peak;
        }

        public String getName() { return name; }
        public MemoryType getType() { return type; }
        public long getUsed() { return used; }
        public long getCommitted() { return committed; }
        public long getMax() { return max; }
        public long getPeak() { return peak; }
    }

    public static class GCInfo {
        private final String name;
        private final long collectionCount;
        private final long collectionTime;

        public GCInfo(String name, long collectionCount, long collectionTime) {
            this.name = name;
            this.collectionCount = collectionCount;
            this.collectionTime = collectionTime;
        }

        public String getName() { return name; }
        public long getCollectionCount() { return collectionCount; }
        public long getCollectionTime() { return collectionTime; }
    }

    // === 분석 결과 클래스들 ===

    public static class MemoryAnalysis {
        private final long startTime, endTime, durationMs;
        private final int snapshotCount;
        private final long heapIncrease, nonHeapIncrease;
        private final long maxHeapUsed, maxNonHeapUsed;
        private final double avgHeapUsed, avgNonHeapUsed;
        private final GCAnalysis gcAnalysis;
        private final LeakDetectionResult leakDetection;
        private final MemoryPressureAnalysis pressureAnalysis;

        public MemoryAnalysis(long startTime, long endTime, long durationMs, int snapshotCount,
                              long heapIncrease, long nonHeapIncrease, long maxHeapUsed, long maxNonHeapUsed,
                              double avgHeapUsed, double avgNonHeapUsed, GCAnalysis gcAnalysis,
                              LeakDetectionResult leakDetection, MemoryPressureAnalysis pressureAnalysis) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMs = durationMs;
            this.snapshotCount = snapshotCount;
            this.heapIncrease = heapIncrease;
            this.nonHeapIncrease = nonHeapIncrease;
            this.maxHeapUsed = maxHeapUsed;
            this.maxNonHeapUsed = maxNonHeapUsed;
            this.avgHeapUsed = avgHeapUsed;
            this.avgNonHeapUsed = avgNonHeapUsed;
            this.gcAnalysis = gcAnalysis;
            this.leakDetection = leakDetection;
            this.pressureAnalysis = pressureAnalysis;
        }

        public static MemoryAnalysis empty() {
            return new MemoryAnalysis(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    new GCAnalysis(0, 0, 0, new HashMap<>()),
                    new LeakDetectionResult(false, 0, "No data"),
                    new MemoryPressureAnalysis(0, 0, false, "No data"));
        }

        // Getters
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getDurationMs() { return durationMs; }
        public int getSnapshotCount() { return snapshotCount; }
        public long getHeapIncrease() { return heapIncrease; }
        public long getNonHeapIncrease() { return nonHeapIncrease; }
        public long getMaxHeapUsed() { return maxHeapUsed; }
        public long getMaxNonHeapUsed() { return maxNonHeapUsed; }
        public double getAvgHeapUsed() { return avgHeapUsed; }
        public double getAvgNonHeapUsed() { return avgNonHeapUsed; }
        public GCAnalysis getGcAnalysis() { return gcAnalysis; }
        public LeakDetectionResult getLeakDetection() { return leakDetection; }
        public MemoryPressureAnalysis getPressureAnalysis() { return pressureAnalysis; }

        public double getHeapIncreaseMB() { return heapIncrease / (1024.0 * 1024.0); }
        public double getMaxHeapUsedMB() { return maxHeapUsed / (1024.0 * 1024.0); }
        public double getAvgHeapUsedMB() { return avgHeapUsed / (1024.0 * 1024.0); }
    }

    public static class GCAnalysis {
        private final long totalGcCount, totalGcTime;
        private final double gcOverhead;
        private final Map<String, Long> gcCountsByCollector;

        public GCAnalysis(long totalGcCount, long totalGcTime, double gcOverhead, Map<String, Long> gcCountsByCollector) {
            this.totalGcCount = totalGcCount;
            this.totalGcTime = totalGcTime;
            this.gcOverhead = gcOverhead;
            this.gcCountsByCollector = new HashMap<>(gcCountsByCollector);
        }

        public long getTotalGcCount() { return totalGcCount; }
        public long getTotalGcTime() { return totalGcTime; }
        public double getGcOverhead() { return gcOverhead; }
        public Map<String, Long> getGcCountsByCollector() { return new HashMap<>(gcCountsByCollector); }
    }

    public static class LeakDetectionResult {
        private final boolean leakSuspected;
        private final double trendPerSecond;
        private final String analysis;

        public LeakDetectionResult(boolean leakSuspected, double trendPerSecond, String analysis) {
            this.leakSuspected = leakSuspected;
            this.trendPerSecond = trendPerSecond;
            this.analysis = analysis;
        }

        public boolean isLeakSuspected() { return leakSuspected; }
        public double getTrendPerSecond() { return trendPerSecond; }
        public String getAnalysis() { return analysis; }
    }

    public static class MemoryPressureAnalysis {
        private final double maxUtilization, avgUtilization;
        private final boolean underPressure;
        private final String analysis;

        public MemoryPressureAnalysis(double maxUtilization, double avgUtilization, boolean underPressure, String analysis) {
            this.maxUtilization = maxUtilization;
            this.avgUtilization = avgUtilization;
            this.underPressure = underPressure;
            this.analysis = analysis;
        }

        public double getMaxUtilization() { return maxUtilization; }
        public double getAvgUtilization() { return avgUtilization; }
        public boolean isUnderPressure() { return underPressure; }
        public String getAnalysis() { return analysis; }
    }
}