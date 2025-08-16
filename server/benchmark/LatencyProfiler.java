package server.benchmark;

import server.core.logging.Logger;
import server.core.logging.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 지연시간(Latency) 분석 도구
 *
 * 분석 항목:
 * 1. 기본 통계 (평균, 중간값, 최소/최대)
 * 2. 백분위수 (50th, 75th, 90th, 95th, 99th, 99.9th)
 * 3. 표준편차 및 분산
 * 4. 지연시간 분포 히스토그램
 * 5. 시간대별 지연시간 추이
 * 6. 아웃라이어 감지
 */
public class LatencyProfiler {

    private static final Logger logger = LoggerFactory.getLogger(LatencyProfiler.class);

    // 기본 백분위수
    private static final double[] DEFAULT_PERCENTILES = {50.0, 75.0, 90.0, 95.0, 99.0, 99.9};

    // 히스토그램 구간 (밀리초)
    private static final double[] HISTOGRAM_BUCKETS = {
            1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000
    };

    public LatencyProfiler() {
    }

    /**
     * 응답시간 목록 분석
     */
    public LatencyStats analyze(List<Double> responseTimesMs) {
        if (responseTimesMs == null || responseTimesMs.isEmpty()) {
            return LatencyStats.empty();
        }

        // 정렬된 복사본 생성
        List<Double> sortedTimes = new ArrayList<>(responseTimesMs);
        sortedTimes.sort(Double::compareTo);

        // 기본 통계
        double min = sortedTimes.get(0);
        double max = sortedTimes.get(sortedTimes.size() - 1);
        double sum = sortedTimes.stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / sortedTimes.size();

        // 중간값
        double median = calculatePercentile(sortedTimes, 50.0);

        // 백분위수 계산
        Map<Double, Double> percentiles = new HashMap<>();
        for (double p : DEFAULT_PERCENTILES) {
            percentiles.put(p, calculatePercentile(sortedTimes, p));
        }

        // 표준편차 계산
        double variance = sortedTimes.stream()
                .mapToDouble(t -> Math.pow(t - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        // 히스토그램 생성
        Map<String, Integer> histogram = createHistogram(sortedTimes);

        // 아웃라이어 감지 (IQR 방법)
        OutlierAnalysis outlierAnalysis = detectOutliers(sortedTimes);

        logger.debug("Latency analysis completed - samples: {}, avg: {:.2f}ms, p95: {:.2f}ms, p99: {:.2f}ms",
                sortedTimes.size(), average, percentiles.get(95.0), percentiles.get(99.0));

        return new LatencyStats(
                sortedTimes.size(),
                min, max, average, median,
                standardDeviation, variance,
                percentiles,
                histogram,
                outlierAnalysis
        );
    }

    /**
     * 시간별 지연시간 분석 (시계열 데이터)
     */
    public TimeSeriesLatencyStats analyzeTimeSeries(List<TimestampedLatency> timestampedLatencies) {
        if (timestampedLatencies == null || timestampedLatencies.isEmpty()) {
            return TimeSeriesLatencyStats.empty();
        }

        // 시간순 정렬
        List<TimestampedLatency> sorted = timestampedLatencies.stream()
                .sorted(Comparator.comparing(TimestampedLatency::getTimestamp))
                .collect(Collectors.toList());

        // 시간 구간별 분석 (1분 단위)
        Map<Long, List<Double>> timeWindows = groupByTimeWindow(sorted, 60000); // 1분

        List<TimeWindowStats> windowStats = new ArrayList<>();
        for (Map.Entry<Long, List<Double>> entry : timeWindows.entrySet()) {
            LatencyStats stats = analyze(entry.getValue());
            windowStats.add(new TimeWindowStats(entry.getKey(), stats));
        }

        // 전체 통계
        List<Double> allLatencies = sorted.stream()
                .map(TimestampedLatency::getLatencyMs)
                .collect(Collectors.toList());
        LatencyStats overallStats = analyze(allLatencies);

        // 추세 분석
        TrendAnalysis trendAnalysis = analyzeTrend(windowStats);

        return new TimeSeriesLatencyStats(
                sorted.get(0).getTimestamp(),
                sorted.get(sorted.size() - 1).getTimestamp(),
                overallStats,
                windowStats,
                trendAnalysis
        );
    }

    /**
     * 백분위수 계산
     */
    private double calculatePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }

        if (percentile <= 0) {
            return sortedValues.get(0);
        }
        if (percentile >= 100) {
            return sortedValues.get(sortedValues.size() - 1);
        }

        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        double weight = index - lowerIndex;
        return sortedValues.get(lowerIndex) * (1 - weight) + sortedValues.get(upperIndex) * weight;
    }

    /**
     * 히스토그램 생성
     */
    private Map<String, Integer> createHistogram(List<Double> sortedTimes) {
        Map<String, Integer> histogram = new LinkedHashMap<>();

        // 구간별 카운트
        for (int i = 0; i < HISTOGRAM_BUCKETS.length; i++) {
            double bucket = HISTOGRAM_BUCKETS[i];
            String label;

            if (i == 0) {
                label = "< " + bucket + "ms";
            } else {
                label = HISTOGRAM_BUCKETS[i-1] + "-" + bucket + "ms";
            }

            int count = 0;
            for (double time : sortedTimes) {
                if ((i == 0 && time < bucket) ||
                        (i > 0 && time >= HISTOGRAM_BUCKETS[i-1] && time < bucket)) {
                    count++;
                }
            }
            histogram.put(label, count);
        }

        // 마지막 구간 (> 최대값)
        double maxBucket = HISTOGRAM_BUCKETS[HISTOGRAM_BUCKETS.length - 1];
        int overMaxCount = (int) sortedTimes.stream()
                .mapToDouble(Double::doubleValue)
                .filter(t -> t >= maxBucket)
                .count();
        histogram.put(">= " + maxBucket + "ms", overMaxCount);

        return histogram;
    }

    /**
     * 아웃라이어 감지 (IQR 방법)
     */
    private OutlierAnalysis detectOutliers(List<Double> sortedTimes) {
        if (sortedTimes.size() < 4) {
            return new OutlierAnalysis(0, 0, new ArrayList<>(), "Insufficient data for outlier detection");
        }

        double q1 = calculatePercentile(sortedTimes, 25.0);
        double q3 = calculatePercentile(sortedTimes, 75.0);
        double iqr = q3 - q1;

        double lowerBound = q1 - 1.5 * iqr;
        double upperBound = q3 + 1.5 * iqr;

        List<Double> outliers = sortedTimes.stream()
                .filter(t -> t < lowerBound || t > upperBound)
                .collect(Collectors.toList());

        int lowerOutliers = (int) sortedTimes.stream()
                .mapToDouble(Double::doubleValue)
                .filter(t -> t < lowerBound)
                .count();

        int upperOutliers = outliers.size() - lowerOutliers;

        String analysis = String.format(
                "IQR: %.2f-%.2f (%.2f), Bounds: %.2f-%.2f, Outliers: %d (%.1f%%)",
                q1, q3, iqr, lowerBound, upperBound, outliers.size(),
                (double) outliers.size() / sortedTimes.size() * 100
        );

        return new OutlierAnalysis(lowerOutliers, upperOutliers, outliers, analysis);
    }

    /**
     * 시간 윈도우별 그룹핑
     */
    private Map<Long, List<Double>> groupByTimeWindow(List<TimestampedLatency> data, long windowSizeMs) {
        Map<Long, List<Double>> windows = new TreeMap<>();

        for (TimestampedLatency item : data) {
            long windowStart = (item.getTimestamp() / windowSizeMs) * windowSizeMs;
            windows.computeIfAbsent(windowStart, k -> new ArrayList<>())
                    .add(item.getLatencyMs());
        }

        return windows;
    }

    /**
     * 추세 분석
     */
    private TrendAnalysis analyzeTrend(List<TimeWindowStats> windowStats) {
        if (windowStats.size() < 2) {
            return new TrendAnalysis(0.0, "STABLE", "Insufficient data for trend analysis");
        }

        // 평균 응답시간의 추세 계산 (선형 회귀)
        List<Double> avgLatencies = windowStats.stream()
                .map(w -> w.getStats().getAverage())
                .collect(Collectors.toList());

        double trend = calculateLinearTrend(avgLatencies);

        // 추세 분류
        String trendDirection;
        if (Math.abs(trend) < 0.1) {
            trendDirection = "STABLE";
        } else if (trend > 0) {
            trendDirection = "INCREASING";
        } else {
            trendDirection = "DECREASING";
        }

        String analysis = String.format(
                "Trend: %s (%.2f ms/window), Windows: %d",
                trendDirection, trend, windowStats.size()
        );

        return new TrendAnalysis(trend, trendDirection, analysis);
    }

    /**
     * 선형 추세 계산
     */
    private double calculateLinearTrend(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    // === 데이터 클래스들 ===

    /**
     * 타임스탬프가 있는 지연시간 데이터
     */
    public static class TimestampedLatency {
        private final long timestamp;
        private final double latencyMs;

        public TimestampedLatency(long timestamp, double latencyMs) {
            this.timestamp = timestamp;
            this.latencyMs = latencyMs;
        }

        public long getTimestamp() { return timestamp; }
        public double getLatencyMs() { return latencyMs; }
    }

    /**
     * 지연시간 통계
     */
    public static class LatencyStats {
        private final int sampleCount;
        private final double min, max, average, median;
        private final double standardDeviation, variance;
        private final Map<Double, Double> percentiles;
        private final Map<String, Integer> histogram;
        private final OutlierAnalysis outlierAnalysis;

        public LatencyStats(int sampleCount, double min, double max, double average, double median,
                            double standardDeviation, double variance, Map<Double, Double> percentiles,
                            Map<String, Integer> histogram, OutlierAnalysis outlierAnalysis) {
            this.sampleCount = sampleCount;
            this.min = min;
            this.max = max;
            this.average = average;
            this.median = median;
            this.standardDeviation = standardDeviation;
            this.variance = variance;
            this.percentiles = new HashMap<>(percentiles);
            this.histogram = new LinkedHashMap<>(histogram);
            this.outlierAnalysis = outlierAnalysis;
        }

        public static LatencyStats empty() {
            return new LatencyStats(0, 0, 0, 0, 0, 0, 0, new HashMap<>(), new LinkedHashMap<>(),
                    new OutlierAnalysis(0, 0, new ArrayList<>(), "No data"));
        }

        // Getters
        public int getSampleCount() { return sampleCount; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getAverage() { return average; }
        public double getMedian() { return median; }
        public double getStandardDeviation() { return standardDeviation; }
        public double getVariance() { return variance; }
        public Map<Double, Double> getPercentiles() { return new HashMap<>(percentiles); }
        public Map<String, Integer> getHistogram() { return new LinkedHashMap<>(histogram); }
        public OutlierAnalysis getOutlierAnalysis() { return outlierAnalysis; }

        // 편의 메서드
        public double getPercentile50() { return percentiles.getOrDefault(50.0, 0.0); }
        public double getPercentile75() { return percentiles.getOrDefault(75.0, 0.0); }
        public double getPercentile90() { return percentiles.getOrDefault(90.0, 0.0); }
        public double getPercentile95() { return percentiles.getOrDefault(95.0, 0.0); }
        public double getPercentile99() { return percentiles.getOrDefault(99.0, 0.0); }
        public double getPercentile999() { return percentiles.getOrDefault(99.9, 0.0); }
    }

    /**
     * 시계열 지연시간 통계
     */
    public static class TimeSeriesLatencyStats {
        private final long startTime, endTime;
        private final LatencyStats overallStats;
        private final List<TimeWindowStats> windowStats;
        private final TrendAnalysis trendAnalysis;

        public TimeSeriesLatencyStats(long startTime, long endTime, LatencyStats overallStats,
                                      List<TimeWindowStats> windowStats, TrendAnalysis trendAnalysis) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.overallStats = overallStats;
            this.windowStats = new ArrayList<>(windowStats);
            this.trendAnalysis = trendAnalysis;
        }

        public static TimeSeriesLatencyStats empty() {
            return new TimeSeriesLatencyStats(0, 0, LatencyStats.empty(), new ArrayList<>(),
                    new TrendAnalysis(0.0, "STABLE", "No data"));
        }

        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public LatencyStats getOverallStats() { return overallStats; }
        public List<TimeWindowStats> getWindowStats() { return new ArrayList<>(windowStats); }
        public TrendAnalysis getTrendAnalysis() { return trendAnalysis; }
    }

    /**
     * 시간 윈도우 통계
     */
    public static class TimeWindowStats {
        private final long windowStart;
        private final LatencyStats stats;

        public TimeWindowStats(long windowStart, LatencyStats stats) {
            this.windowStart = windowStart;
            this.stats = stats;
        }

        public long getWindowStart() { return windowStart; }
        public LatencyStats getStats() { return stats; }
    }

    /**
     * 아웃라이어 분석
     */
    public static class OutlierAnalysis {
        private final int lowerOutliers, upperOutliers;
        private final List<Double> outlierValues;
        private final String analysis;

        public OutlierAnalysis(int lowerOutliers, int upperOutliers, List<Double> outlierValues, String analysis) {
            this.lowerOutliers = lowerOutliers;
            this.upperOutliers = upperOutliers;
            this.outlierValues = new ArrayList<>(outlierValues);
            this.analysis = analysis;
        }

        public int getLowerOutliers() { return lowerOutliers; }
        public int getUpperOutliers() { return upperOutliers; }
        public int getTotalOutliers() { return lowerOutliers + upperOutliers; }
        public List<Double> getOutlierValues() { return new ArrayList<>(outlierValues); }
        public String getAnalysis() { return analysis; }
    }

    /**
     * 추세 분석
     */
    public static class TrendAnalysis {
        private final double trendSlope;
        private final String direction;
        private final String analysis;

        public TrendAnalysis(double trendSlope, String direction, String analysis) {
            this.trendSlope = trendSlope;
            this.direction = direction;
            this.analysis = analysis;
        }

        public double getTrendSlope() { return trendSlope; }
        public String getDirection() { return direction; }
        public String getAnalysis() { return analysis; }
    }
}