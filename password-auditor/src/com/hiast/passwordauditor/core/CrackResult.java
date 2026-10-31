package com.hiast.passwordauditor.core;

/**
 * Immutable result of a single sequential or parallel crack attempt.
 * Kept free of any threading or UI concerns so it can be reused by the
 * core engine, the benchmark module, and the GUI alike.
 */
public final class CrackResult {

    private final boolean found;
    private final String crackedPassword; // null if not found
    private final long candidatesTried;
    private final long elapsedNanos;
    private final int threadsUsed;

    public CrackResult(boolean found, String crackedPassword, long candidatesTried,
                        long elapsedNanos, int threadsUsed) {
        this.found = found;
        this.crackedPassword = crackedPassword;
        this.candidatesTried = candidatesTried;
        this.elapsedNanos = elapsedNanos;
        this.threadsUsed = threadsUsed;
    }

    public boolean isFound() {
        return found;
    }

    public String getCrackedPassword() {
        return crackedPassword;
    }

    public long getCandidatesTried() {
        return candidatesTried;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public double getElapsedSeconds() {
        return elapsedNanos / 1_000_000_000.0;
    }

    public int getThreadsUsed() {
        return threadsUsed;
    }

    /** Throughput in hashes-per-second, the headline metric for this assignment. */
    public double getHashesPerSecond() {
        double seconds = getElapsedSeconds();
        return seconds > 0 ? candidatesTried / seconds : 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "CrackResult{found=%s, password=%s, candidatesTried=%d, elapsedSec=%.3f, threads=%d, hashes/sec=%.0f}",
                found, crackedPassword, candidatesTried, getElapsedSeconds(), threadsUsed, getHashesPerSecond());
    }
}
