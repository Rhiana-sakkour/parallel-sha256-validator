package com.hiast.passwordauditor.core;

/**
 * Contract for a brute-force SHA-256 password cracker, implemented in both
 * a sequential (single-threaded) and a parallel (multi-threaded) form so
 * the two can be benchmarked head-to-head, as required by the assignment.
 */
public interface HashCracker {

    /**
     * Scans the entire candidate space in a single thread, from index 0
     * upward, stopping as soon as a candidate's hash matches targetHash
     * (or the space is exhausted).
     */
    CrackResult crackSequential(byte[] targetHash, CandidateSpace space);

    /**
     * Partitions the candidate space into {@code numThreads} contiguous,
     * equally sized ranges and scans them concurrently, stopping all
     * threads as soon as any one of them finds a match.
     */
    CrackResult crackParallel(byte[] targetHash, CandidateSpace space, int numThreads);
}
