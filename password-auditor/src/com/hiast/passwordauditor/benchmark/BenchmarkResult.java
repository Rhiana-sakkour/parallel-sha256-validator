package com.hiast.passwordauditor.benchmark;

/**
 * One row of the benchmark table/graph required by the assignment report:
 * execution time and derived Speedup for a given thread count N.
 */
public final class BenchmarkResult {

    private final int threadCount;
    private final long candidatesHashed;
    private final long elapsedNanos;
    private final double speedupVsSequential; // T_sequential / T_parallel

    public BenchmarkResult(int threadCount, long candidatesHashed, long elapsedNanos,
                            double speedupVsSequential) {
        this.threadCount = threadCount;
        this.candidatesHashed = candidatesHashed;
        this.elapsedNanos = elapsedNanos;
        this.speedupVsSequential = speedupVsSequential;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public long getCandidatesHashed() {
        return candidatesHashed;
    }

    public double getElapsedSeconds() {
        return elapsedNanos / 1_000_000_000.0;
    }

    public double getHashesPerSecond() {
        double seconds = getElapsedSeconds();
        return seconds > 0 ? candidatesHashed / seconds : 0.0;
    }

    public double getSpeedupVsSequential() {
        return speedupVsSequential;
    }

    /** Parallel efficiency = Speedup / N; ideal value is 1.0 (100%). */
    public double getEfficiency() {
        return threadCount > 0 ? speedupVsSequential / threadCount : 0.0;
    }

    public String toCsvRow() {
        return String.format("%d,%d,%.6f,%.0f,%.3f,%.3f",
                threadCount, candidatesHashed, getElapsedSeconds(),
                getHashesPerSecond(), speedupVsSequential, getEfficiency());
    }

    public static String csvHeader() {
        return "threads,candidates_hashed,elapsed_seconds,hashes_per_second,speedup,efficiency";
    }
}
