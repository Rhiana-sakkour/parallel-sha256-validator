package com.hiast.passwordauditor.benchmark;

import com.hiast.passwordauditor.core.CandidateSpace;
import com.hiast.passwordauditor.util.Sha256Util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure-throughput benchmark harness, deliberately SEPARATE from
 * {@link com.hiast.passwordauditor.core.BruteForceHashCracker}'s crack
 * logic. A crack attempt's duration depends on WHERE the target password
 * happens to sit in the search space, which would make a thread-count
 * comparison noisy and unfair. This class instead always hashes the exact
 * same fixed number of candidates (no target, no early exit), so the only
 * variable between runs is the thread count — giving a clean, reproducible
 * Speedup curve for the required N = 1, 2, 4, 8, 16 comparison.
 */
public final class BenchmarkRunner {

    /**
     * Hashes exactly {@code candidateCount} candidates (or fewer if the
     * space is smaller) split evenly across {@code numThreads} worker
     * threads, discarding the resulting hashes (throughput is all that
     * matters here).
     */
    public BenchmarkResult runFixedWorkload(CandidateSpace space, long candidateCount, int numThreads)
            throws InterruptedException {
        if (numThreads < 1) {
            throw new IllegalArgumentException("numThreads must be >= 1.");
        }
        long total = Math.min(candidateCount, space.totalCandidates());
        int effectiveThreads = (int) Math.min(numThreads, Math.max(1, total));

        long baseChunk = total / effectiveThreads;
        long remainder = total % effectiveThreads;

        ExecutorService pool = Executors.newFixedThreadPool(effectiveThreads);
        List<Callable<Void>> tasks = new ArrayList<>(effectiveThreads);

        long rangeStart = 0;
        for (int t = 0; t < effectiveThreads; t++) {
            long chunkSize = baseChunk + (t < remainder ? 1 : 0);
            long rangeEnd = rangeStart + chunkSize;
            final long threadRangeStart = rangeStart;
            final long threadRangeEnd = rangeEnd;

            tasks.add(() -> {
                MessageDigest digest = Sha256Util.newDigest(); // one per thread, see Sha256Util
                for (long i = threadRangeStart; i < threadRangeEnd; i++) {
                    String candidate = space.candidateAt(i);
                    Sha256Util.hash(digest, candidate); // result intentionally discarded: throughput only
                }
                return null;
            });

            rangeStart = rangeEnd;
        }

        long startTime = System.nanoTime();
        try {
            pool.invokeAll(tasks);
        } finally {
            pool.shutdown();
        }
        long elapsed = System.nanoTime() - startTime;

        // Speedup is filled in afterwards by runSuite(), once the N=1
        // baseline is known; a standalone call reports speedup = 1.0.
        return new BenchmarkResult(effectiveThreads, total, elapsed, 1.0);
    }

    /**
     * Runs the full benchmark suite required by the report. The FIRST
     * entry of {@code threadCounts} MUST be 1 — it establishes the
     * sequential baseline (T_sequential) that every other entry's Speedup
     * is measured against.
     */
    public List<BenchmarkResult> runSuite(CandidateSpace space, long candidateCount, int[] threadCounts)
            throws InterruptedException {
        if (threadCounts == null || threadCounts.length == 0 || threadCounts[0] != 1) {
            throw new IllegalArgumentException(
                    "threadCounts must be non-empty and start with 1 (the sequential baseline).");
        }

        List<BenchmarkResult> rawResults = new ArrayList<>(threadCounts.length);
        for (int n : threadCounts) {
            rawResults.add(runFixedWorkload(space, candidateCount, n));
        }

        double sequentialSeconds = rawResults.get(0).getElapsedSeconds();

        List<BenchmarkResult> finalResults = new ArrayList<>(rawResults.size());
        for (BenchmarkResult r : rawResults) {
            double speedup = sequentialSeconds / r.getElapsedSeconds();
            long elapsedNanos = Math.round(r.getElapsedSeconds() * 1_000_000_000.0);
            finalResults.add(new BenchmarkResult(r.getThreadCount(), r.getCandidatesHashed(), elapsedNanos, speedup));
        }
        return finalResults;
    }

    /** Exports the suite results to CSV for plotting in the required report graph. */
    public void exportToCsv(List<BenchmarkResult> results, Path outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write(BenchmarkResult.csvHeader());
            writer.newLine();
            for (BenchmarkResult r : results) {
                writer.write(r.toCsvRow());
                writer.newLine();
            }
        }
    }
}
