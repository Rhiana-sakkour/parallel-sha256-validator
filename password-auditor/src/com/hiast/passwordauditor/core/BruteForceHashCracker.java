package com.hiast.passwordauditor.core;

import com.hiast.passwordauditor.util.Sha256Util;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Brute-force SHA-256 password cracker: sequential and parallel versions
 * of the SAME algorithm, so their execution times can be compared fairly.
 *
 * Synchronization primitives used here, and why each one was chosen:
 *  - {@link ExecutorService} (fixed Thread Pool): bounds the number of OS
 *    threads to exactly the number the user asked to benchmark, instead of
 *    spawning one raw Thread per unit of work.
 *  - {@link AtomicBoolean} "found": lock-free flag polled by every worker
 *    so all threads can stop early the moment any one of them succeeds.
 *  - {@link AtomicLong} "totalTried": lock-free running total of hashes
 *    computed, used to report aggregate throughput (hashes/sec).
 *  - {@link ReentrantLock} "resultLock": guards the one short critical
 *    section where the winning candidate is recorded, so a result can
 *    never be partially/incorrectly overwritten if two threads match at
 *    almost the same instant.
 *
 * Deadlock avoidance: only ONE lock exists in this class, no thread ever
 * holds it while waiting on anything else, and the critical section it
 * guards is a handful of field assignments — so circular waits are
 * structurally impossible here.
 */
public final class BruteForceHashCracker implements HashCracker {

    /**
     * How many candidates a worker processes between (a) polling the shared
     * "found" flag and (b) flushing its local counter into the shared
     * atomic counter. Checking/flushing on every single hash would be
     * correct but adds synchronization overhead on the hot path; batching
     * amortizes that cost while keeping early-exit latency negligible.
     */
    private static final long PROGRESS_FLUSH_INTERVAL = 2_000L;

    @Override
    public CrackResult crackSequential(byte[] targetHash, CandidateSpace space) {
        long total = space.totalCandidates();
        MessageDigest digest = Sha256Util.newDigest(); // one instance for the whole scan

        long startTime = System.nanoTime();
        for (long i = 0; i < total; i++) {
            String candidate = space.candidateAt(i);
            byte[] candidateHash = Sha256Util.hash(digest, candidate);
            if (Sha256Util.matches(candidateHash, targetHash)) {
                long elapsed = System.nanoTime() - startTime;
                return new CrackResult(true, candidate, i + 1, elapsed, 1);
            }
        }
        long elapsed = System.nanoTime() - startTime;
        return new CrackResult(false, null, total, elapsed, 1);
    }

    @Override
    public CrackResult crackParallel(byte[] targetHash, CandidateSpace space, int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("numThreads must be >= 1.");
        }
        long total = space.totalCandidates();
        int effectiveThreads = (int) Math.min(numThreads, Math.max(1, total));

        AtomicBoolean found = new AtomicBoolean(false);
        AtomicLong totalTried = new AtomicLong(0);
        Lock resultLock = new ReentrantLock();
        final String[] crackedPasswordHolder = new String[1];

        long baseChunk = total / effectiveThreads;
        long remainder = total % effectiveThreads;

        ExecutorService pool = Executors.newFixedThreadPool(effectiveThreads);
        List<Callable<Void>> tasks = new ArrayList<>(effectiveThreads);

        long rangeStart = 0;
        for (int t = 0; t < effectiveThreads; t++) {
            long chunkSize = baseChunk + (t < remainder ? 1 : 0);
            long rangeEnd = rangeStart + chunkSize; // exclusive
            final long threadRangeStart = rangeStart;
            final long threadRangeEnd = rangeEnd;

            tasks.add(() -> {
                // Each worker owns its OWN MessageDigest instance for its
                // entire scan: MessageDigest is not thread-safe, so sharing
                // a single instance across threads would silently corrupt
                // the computed hashes.
                MessageDigest digest = Sha256Util.newDigest();
                long localCount = 0;

                for (long i = threadRangeStart; i < threadRangeEnd; i++) {
                    if ((i - threadRangeStart) % PROGRESS_FLUSH_INTERVAL == 0 && found.get()) {
                        break; // another thread already found the answer
                    }

                    String candidate = space.candidateAt(i);
                    byte[] candidateHash = Sha256Util.hash(digest, candidate);
                    localCount++;

                    if (Sha256Util.matches(candidateHash, targetHash)) {
                        resultLock.lock();
                        try {
                            if (!found.get()) {
                                found.set(true);
                                crackedPasswordHolder[0] = candidate;
                            }
                        } finally {
                            resultLock.unlock();
                        }
                        break;
                    }

                    if (localCount % PROGRESS_FLUSH_INTERVAL == 0) {
                        totalTried.addAndGet(PROGRESS_FLUSH_INTERVAL);
                        localCount = 0;
                    }
                }
                totalTried.addAndGet(localCount); // flush remainder
                return null;
            });

            rangeStart = rangeEnd;
        }

        long startTime = System.nanoTime();
        try {
            // invokeAll() blocks the calling thread until every submitted
            // task has finished. This is our synchronization barrier: it is
            // safe to read the shared result fields the instant it returns.
            pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel crack was interrupted.", e);
        } finally {
            pool.shutdown();
        }
        long elapsed = System.nanoTime() - startTime;

        boolean wasFound = found.get();
        String password = wasFound ? crackedPasswordHolder[0] : null;
        return new CrackResult(wasFound, password, totalTried.get(), elapsed, effectiveThreads);
    }
}
