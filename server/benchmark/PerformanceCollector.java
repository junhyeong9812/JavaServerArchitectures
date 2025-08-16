package server.benchmark;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * 성능 메트릭 수집기
 *
 * 수집 항목:
 * 1. CPU 사용률
 * 2. 메모리 사용량 (Heap, Non-Heap)
 * 3. 스레드 수
 * 4. GC 정보
 * 5. 네트워크 I/O
 * 6. 시스템 로드
 */
public class PerformanceCollector {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceCollector.class);

    // JMX Bean들
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    // 수집 제어
    private final AtomicBoolean collecting = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final List<PerformanceSnapshot> snapshots;
    private ScheduledFuture<?> collectionTask;

    // 수집 설정
    private volatile int collectionIntervalMs = 1000; // 1초마다
    private volatile boolean enableDetailedGC = true;
    private volatile boolean enableThreadDetail = true;

    public PerformanceCollector() {
        // JMX Bean 초기화
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PerformanceCollector");
            t.setDaemon(true);
            return t;
        });

        this.snapshots = Collections.synchronizedList(new ArrayList<>());

        logger.info("PerformanceCollector initialized - available processors: {}",
                Runtime.getRuntime().availableProcessors());
    }

    /**
     * 성능 수집 시작
     */
    public void startCollection() {
        if (!collecting.compareAndSet(false, true)) {
            logger.warn("Performance collection already started");
            return;
        }

        logger.info("Starting performance collection (interval: {}ms)", collectionIntervalMs);

        // 초기 스냅샷
        snapshots.clear();
        takeSnapshot();

        // 주기적 수집 시작
        collectionTask = scheduler.scheduleAtFixedRate(
                this::takeSnapshot,
                collectionIntervalMs,
                collectionIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 성능 수집 중지
     */
    public void stopCollection() {
        if (!collecting.compareAndSet(true, false)) {
            logger.warn("Performance collection not started");
            return;
        }

        if (collectionTask != null) {
            collectionTask.cancel(false);
        }

        // 최종 스냅샷
        takeSnapshot();

        logger.info("Performance collection stopped - total snapshots: {}", snapshots.size());
    }

    /**
     * 현재 시점의 성능 스냅샷 수집
     */
    private void takeSnapshot() {
        try {
            long timestamp = System.currentTimeMillis();

            // 메모리 정보
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

            // 스레드 정보
            int threadCount = threadBean.getThreadCount();
            int peakThreadCount = threadBean.getPeakThreadCount();
            int daemonThreadCount = threadBean.getDaemonThreadCount();

            // CPU 정보
            double cpuUsage = getCpuUsage();
            double systemLoadAverage = osBean.getSystemLoadAverage();

            // GC 정보
            GCInfo gcInfo = collectGCInfo();

            // 시스템 정보
            long freeMemory = Runtime.getRuntime().freeMemory();
            long totalMemory = Runtime.getRuntime().totalMemory();
            long maxMemory = Runtime.getRuntime().maxMemory();

            PerformanceSnapshot snapshot = new PerformanceSnapshot(
                    timestamp,

                    // 메모리
                    heapUsage.getUsed(),
                    heapUsage.getMax(),
                    nonHeapUsage.getUsed(),
                    nonHeapUsage.getMax(),
                    freeMemory,
                    totalMemory,
                    maxMemory,

                    // 스레드
                    threadCount,
                    peakThreadCount,
                    daemonThreadCount,

                    // CPU
                    cpuUsage,
                    systemLoadAverage,

                    // GC
                    gcInfo
            );

            snapshots.add(snapshot);

            logger.debug("Performance snapshot taken - heap: {}MB, threads: {}, cpu: {:.1f}%",
                    heapUsage.getUsed() / (1024 * 1024), threadCount, cpuUsage * 100);

        } catch (Exception e) {
            logger.error("Error taking performance snapshot", e);
        }
    }

    /**
     * CPU 사용률 계산
     */
    private double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean =
                    (com.sun.management.OperatingSystemMXBean) osBean;
            return sunOsBean.getProcessCpuLoad();
        }
        return -1.0; // 정보 없음
    }

    /**
     * GC 정보 수집
     */
    private GCInfo collectGCInfo() {
        long totalCollectionCount = 0;
        long totalCollectionTime = 0;
        Map<String, Long> gcCounts = new HashMap<>();
        Map<String, Long> gcTimes = new HashMap<>();

        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long collectionCount = gcBean.getCollectionCount();
            long collectionTime = gcBean.getCollectionTime();

            if (collectionCount >= 0) {
                totalCollectionCount += collectionCount;
                gcCounts.put(gcName, collectionCount);
            }

            if (collectionTime >= 0) {
                totalCollectionTime += collectionTime;
                gcTimes.put(gcName, collectionTime);
            }
        }

        return new GCInfo(totalCollectionCount, totalCollectionTime, gcCounts, gcTimes);
    }

    /**
     * 수집된 스냅샷들로부터 통계 생성
     */
    public PerformanceStats generateStats() {
        if (snapshots.isEmpty()) {
            return PerformanceStats.empty();
        }

        List<PerformanceSnapshot> sortedSnapshots = new ArrayList<>(snapshots);
        sortedSnapshots.sort(Comparator.comparing(PerformanceSnapshot::getTimestamp));

        PerformanceSnapshot first = sortedSnapshots.get(0);
        PerformanceSnapshot last = sortedSnapshots.get(sortedSnapshots.size() - 1);
        long durationMs = last.getTimestamp() - first.getTimestamp();

        // 메모리 통계
        MemoryStats memoryStats = calculateMemoryStats(sortedSnapshots);

        // 스레드 통계
        ThreadStats threadStats = calculateThreadStats(sortedSnapshots);

        // CPU 통계
        CpuStats cpuStats = calculateCpuStats(sortedSnapshots);

        // GC 통계
        GCStats gcStats = calculateGCStats(sortedSnapshots);

        return new PerformanceStats(
                first.getTimestamp(),
                last.getTimestamp(),
                durationMs,
                snapshots.size(),
                memoryStats,
                threadStats,
                cpuStats,
                gcStats
        );
    }

    /**
     * 메모리 통계 계산
     */
    private MemoryStats calculateMemoryStats(List<PerformanceSnapshot> snapshots) {
        LongSummaryStatistics heapUsed = snapshots.stream()
                .mapToLong(PerformanceSnapshot::getHeapUsed)
                .summaryStatistics();

        LongSummaryStatistics nonHeapUsed = snapshots.stream()
                .mapToLong(PerformanceSnapshot::getNonHeapUsed)
                .summaryStatistics();

        long maxHeap = snapshots.stream()
                .mapToLong(PerformanceSnapshot::getHeapMax)
                .max().orElse(0);

        long maxNonHeap = snapshots.stream()
                .mapToLong(PerformanceSnapshot::getNonHeapMax)
                .max().orElse(0);

        return new MemoryStats(
                heapUsed.getAverage(), heapUsed.getMin(), heapUsed.getMax(), maxHeap,
                nonHeapUsed.getAverage(), nonHeapUsed.getMin(), nonHeapUsed.getMax(), maxNonHeap
        );
    }

    /**
     * 스레드 통계 계산
     */
    private ThreadStats calculateThreadStats(List<PerformanceSnapshot> snapshots) {
        IntSummaryStatistics threadCount = snapshots.stream()
                .mapToInt(PerformanceSnapshot::getThreadCount)
                .summaryStatistics();

        int peakThreads = snapshots.stream()
                .mapToInt(PerformanceSnapshot::getPeakThreadCount)
                .max().orElse(0);

        return new ThreadStats(
                threadCount.getAverage(),
                threadCount.getMin(),
                threadCount.getMax(),
                peakThreads
        );
    }

    /**
     * CPU 통계 계산
     */
    private CpuStats calculateCpuStats(List<PerformanceSnapshot> snapshots) {
        DoubleSummaryStatistics cpuUsage = snapshots.stream()
                .filter(s -> s.getCpuUsage() >= 0)
                .mapToDouble(PerformanceSnapshot::getCpuUsage)
                .summaryStatistics();

        DoubleSummaryStatistics loadAverage = snapshots.stream()
                .filter(s -> s.getSystemLoadAverage() >= 0)
                .mapToDouble(PerformanceSnapshot::getSystemLoadAverage)
                .summaryStatistics();

        return new CpuStats(
                cpuUsage.getAverage(),
                cpuUsage.getMin(),
                cpuUsage.getMax(),
                loadAverage.getAverage()
        );
    }

    /**
     * GC 통계 계산
     */
    private GCStats calculateGCStats(List<PerformanceSnapshot> snapshots) {
        if (snapshots.size() < 2) {
            return new GCStats(0, 0, 0, new HashMap<>());
        }

        PerformanceSnapshot first = snapshots.get(0);
        PerformanceSnapshot last = snapshots.get(snapshots.size() - 1);

        long gcCount = last.getGcInfo().getTotalCollectionCount() -
                first.getGcInfo().getTotalCollectionCount();

        long gcTime = last.getGcInfo().getTotalCollectionTime() -
                first.getGcInfo().getTotalCollectionTime();

        double gcOverhead = gcCount > 0 ? (double) gcTime / (last.getTimestamp() - first.getTimestamp()) * 100 : 0;

        // GC별 통계
        Map<String, Long> gcCountByType = new HashMap<>();
        for (String gcName : first.getGcInfo().getGcCounts().keySet()) {
            long firstCount = first.getGcInfo().getGcCounts().getOrDefault(gcName, 0L);
            long lastCount = last.getGcInfo().getGcCounts().getOrDefault(gcName, 0L);
            gcCountByType.put(gcName, lastCount - firstCount);
        }

        return new GCStats(gcCount, gcTime, gcOverhead, gcCountByType);
    }

    /**
     * 수집 설정
     */
    public void setCollectionInterval(int intervalMs) {
        this.collectionIntervalMs = intervalMs;
    }

    public void setEnableDetailedGC(boolean enable) {
        this.enableDetailedGC = enable;
    }

    public void setEnableThreadDetail(boolean enable) {
        this.enableThreadDetail = enable;
    }

    /**
     * 현재 상태
     */
    public boolean isCollecting() {
        return collecting.get();
    }

    public int getSnapshotCount() {
        return snapshots.size();
    }

    /**
     * 스냅샷 목록 (읽기 전용)
     */
    public List<PerformanceSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    /**
     * 리소스 정리
     */
    public void shutdown() {
        stopCollection();
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

    // === 내부 데이터 클래스들 ===

    /**
     * 성능 스냅샷
     */
    public static class PerformanceSnapshot {
        private final long timestamp;
        private final long heapUsed, heapMax, nonHeapUsed, nonHeapMax;
        private final long freeMemory, totalMemory, maxMemory;
        private final int threadCount, peakThreadCount, daemonThreadCount;
        private final double cpuUsage, systemLoadAverage;
        private final GCInfo gcInfo;

        public PerformanceSnapshot(long timestamp, long heapUsed, long heapMax,
                                   long nonHeapUsed, long nonHeapMax, long freeMemory,
                                   long totalMemory, long maxMemory, int threadCount,
                                   int peakThreadCount, int daemonThreadCount,
                                   double cpuUsage, double systemLoadAverage, GCInfo gcInfo) {
            this.timestamp = timestamp;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
            this.freeMemory = freeMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.daemonThreadCount = daemonThreadCount;
            this.cpuUsage = cpuUsage;
            this.systemLoadAverage = systemLoadAverage;
            this.gcInfo = gcInfo;
        }

        // Getters
        public long getTimestamp() { return timestamp; }
        public long getHeapUsed() { return heapUsed; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapMax() { return nonHeapMax; }
        public long getFreeMemory() { return freeMemory; }
        public long getTotalMemory() { return totalMemory; }
        public long getMaxMemory() { return maxMemory; }
        public int getThreadCount() { return threadCount; }
        public int getPeakThreadCount() { return peakThreadCount; }
        public int getDaemonThreadCount() { return daemonThreadCount; }
        public double getCpuUsage() { return cpuUsage; }
        public double getSystemLoadAverage() { return systemLoadAverage; }
        public GCInfo getGcInfo() { return gcInfo; }
    }

    /**
     * GC 정보
     */
    public static class GCInfo {
        private final long totalCollectionCount;
        private final long totalCollectionTime;
        private final Map<String, Long> gcCounts;
        private final Map<String, Long> gcTimes;

        public GCInfo(long totalCollectionCount, long totalCollectionTime,
                      Map<String, Long> gcCounts, Map<String, Long> gcTimes) {
            this.totalCollectionCount = totalCollectionCount;
            this.totalCollectionTime = totalCollectionTime;
            this.gcCounts = new HashMap<>(gcCounts);
            this.gcTimes = new HashMap<>(gcTimes);
        }

        public long getTotalCollectionCount() { return totalCollectionCount; }
        public long getTotalCollectionTime() { return totalCollectionTime; }
        public Map<String, Long> getGcCounts() { return new HashMap<>(gcCounts); }
        public Map<String, Long> getGcTimes() { return new HashMap<>(gcTimes); }
    }

    // === 통계 클래스들 ===

    public static class PerformanceStats {
        private final long startTime, endTime, durationMs;
        private final int snapshotCount;
        private final MemoryStats memoryStats;
        private final ThreadStats threadStats;
        private final CpuStats cpuStats;
        private final GCStats gcStats;

        public PerformanceStats(long startTime, long endTime, long durationMs, int snapshotCount,
                                MemoryStats memoryStats, ThreadStats threadStats,
                                CpuStats cpuStats, GCStats gcStats) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMs = durationMs;
            this.snapshotCount = snapshotCount;
            this.memoryStats = memoryStats;
            this.threadStats = threadStats;
            this.cpuStats = cpuStats;
            this.gcStats = gcStats;
        }

        public static PerformanceStats empty() {
            return new PerformanceStats(0, 0, 0, 0,
                    new MemoryStats(0, 0, 0, 0, 0, 0, 0, 0),
                    new ThreadStats(0, 0, 0, 0),
                    new CpuStats(0, 0, 0, 0),
                    new GCStats(0, 0, 0, new HashMap<>()));
        }

        // Getters
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getDurationMs() { return durationMs; }
        public int getSnapshotCount() { return snapshotCount; }
        public MemoryStats getMemoryStats() { return memoryStats; }
        public ThreadStats getThreadStats() { return threadStats; }
        public CpuStats getCpuStats() { return cpuStats; }
        public GCStats getGcStats() { return gcStats; }
    }

    public static class MemoryStats {
        private final double avgHeapUsed, minHeapUsed, maxHeapUsed, maxHeap;
        private final double avgNonHeapUsed, minNonHeapUsed, maxNonHeapUsed, maxNonHeap;

        public MemoryStats(double avgHeapUsed, double minHeapUsed, double maxHeapUsed, double maxHeap,
                           double avgNonHeapUsed, double minNonHeapUsed, double maxNonHeapUsed, double maxNonHeap) {
            this.avgHeapUsed = avgHeapUsed;
            this.minHeapUsed = minHeapUsed;
            this.maxHeapUsed = maxHeapUsed;
            this.maxHeap = maxHeap;
            this.avgNonHeapUsed = avgNonHeapUsed;
            this.minNonHeapUsed = minNonHeapUsed;
            this.maxNonHeapUsed = maxNonHeapUsed;
            this.maxNonHeap = maxNonHeap;
        }

        // Getters
        public double getAvgHeapUsedMB() { return avgHeapUsed / (1024 * 1024); }
        public double getMinHeapUsedMB() { return minHeapUsed / (1024 * 1024); }
        public double getMaxHeapUsedMB() { return maxHeapUsed / (1024 * 1024); }
        public double getMaxHeapMB() { return maxHeap / (1024 * 1024); }
        public double getAvgNonHeapUsedMB() { return avgNonHeapUsed / (1024 * 1024); }
        public double getHeapUtilization() { return maxHeap > 0 ? (maxHeapUsed / maxHeap) * 100 : 0; }
    }

    public static class ThreadStats {
        private final double avgThreadCount;
        private final int minThreadCount, maxThreadCount, peakThreadCount;

        public ThreadStats(double avgThreadCount, int minThreadCount, int maxThreadCount, int peakThreadCount) {
            this.avgThreadCount = avgThreadCount;
            this.minThreadCount = minThreadCount;
            this.maxThreadCount = maxThreadCount;
            this.peakThreadCount = peakThreadCount;
        }

        public double getAvgThreadCount() { return avgThreadCount; }
        public int getMinThreadCount() { return minThreadCount; }
        public int getMaxThreadCount() { return maxThreadCount; }
        public int getPeakThreadCount() { return peakThreadCount; }
    }

    public static class CpuStats {
        private final double avgCpuUsage, minCpuUsage, maxCpuUsage, avgLoadAverage;

        public CpuStats(double avgCpuUsage, double minCpuUsage, double maxCpuUsage, double avgLoadAverage) {
            this.avgCpuUsage = avgCpuUsage;
            this.minCpuUsage = minCpuUsage;
            this.maxCpuUsage = maxCpuUsage;
            this.avgLoadAverage = avgLoadAverage;
        }

        public double getAvgCpuUsagePercent() { return avgCpuUsage * 100; }
        public double getMinCpuUsagePercent() { return minCpuUsage * 100; }
        public double getMaxCpuUsagePercent() { return maxCpuUsage * 100; }
        public double getAvgLoadAverage() { return avgLoadAverage; }
    }

    public static class GCStats {
        private final long totalGcCount, totalGcTime;
        private final double gcOverheadPercent;
        private final Map<String, Long> gcCountByType;

        public GCStats(long totalGcCount, long totalGcTime, double gcOverheadPercent, Map<String, Long> gcCountByType) {
            this.totalGcCount = totalGcCount;
            this.totalGcTime = totalGcTime;
            this.gcOverheadPercent = gcOverheadPercent;
            this.gcCountByType = new HashMap<>(gcCountByType);
        }

        public long getTotalGcCount() { return totalGcCount; }
        public long getTotalGcTime() { return totalGcTime; }
        public double getGcOverheadPercent() { return gcOverheadPercent; }
        public Map<String, Long> getGcCountByType() { return new HashMap<>(gcCountByType); }
    }
}