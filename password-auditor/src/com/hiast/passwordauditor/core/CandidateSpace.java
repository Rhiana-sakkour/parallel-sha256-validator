package com.hiast.passwordauditor.core;

/**
 * Represents the full brute-force search space of candidate passwords for a
 * given character set and maximum length, and provides a pure function
 * mapping a global 0-based index to the corresponding candidate string.
 *
 * WHY THIS MATTERS FOR PARALLELISM:
 * Because {@link #candidateAt(long)} is a pure, stateless function of its
 * index, the total index range [0, totalCandidates()) can be split into
 * contiguous blocks and handed to worker threads WITHOUT any shared mutable
 * iterator, queue, or lock. Each thread only needs to know its own
 * [start, end) range — this is classic "embarrassingly parallel" data
 * decomposition (Data Parallelism), which is exactly what the assignment
 * asks for: "توزيع نطاق الاحتمالات أو فضاء العينة بالتساوي على عدد النياسب".
 *
 * Candidates are enumerated by increasing length first (all length-1
 * strings, then all length-2 strings, ...), and within a fixed length by
 * treating the candidate as a base-N number (N = charset size) using
 * standard positional numeral conversion.
 */
public final class CandidateSpace {

    private final char[] charset;
    private final int maxLength;

    /**
     * cumulativeCounts[l] = number of candidates with length <= l.
     * cumulativeCounts[0] = 0 by definition.
     */
    private final long[] cumulativeCounts;

    public CandidateSpace(char[] charset, int maxLength) {
        if (charset == null || charset.length == 0) {
            throw new IllegalArgumentException("Charset must not be empty.");
        }
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be >= 1.");
        }

        this.charset = charset.clone();
        this.maxLength = maxLength;
        this.cumulativeCounts = new long[maxLength + 1];

        long charsetSize = charset.length;
        long cumulative = 0;
        for (int length = 1; length <= maxLength; length++) {
            long countAtThisLength = exactPow(charsetSize, length);
            cumulative = Math.addExact(cumulative, countAtThisLength);
            cumulativeCounts[length] = cumulative;
        }
    }

    /** Total number of candidate passwords in this space (all lengths combined). */
    public long totalCandidates() {
        return cumulativeCounts[maxLength];
    }

    public int getMaxLength() {
        return maxLength;
    }

    public int getCharsetSize() {
        return charset.length;
    }

    /**
     * Maps a global index in [0, totalCandidates()) to its candidate string.
     * This is a pure function: calling it with the same index always
     * produces the same string, from any thread, with no shared state.
     */
    public String candidateAt(long globalIndex) {
        if (globalIndex < 0 || globalIndex >= totalCandidates()) {
            throw new IndexOutOfBoundsException(
                    "Index " + globalIndex + " is outside [0, " + totalCandidates() + ")");
        }

        // Step 1: find which length-bucket this index falls into.
        int length = 1;
        while (cumulativeCounts[length] <= globalIndex) {
            length++;
        }
        long localIndex = globalIndex - cumulativeCounts[length - 1];

        // Step 2: convert localIndex to a base-(charsetSize) representation
        // with exactly `length` digits, each digit mapped through the charset.
        char[] result = new char[length];
        long base = charset.length;
        for (int position = length - 1; position >= 0; position--) {
            int digit = (int) (localIndex % base);
            result[position] = charset[digit];
            localIndex /= base;
        }
        return new String(result);
    }

    /**
     * Integer power with overflow detection. A password space can grow
     * astronomically large (e.g. 95^8), so silently overflowing into a
     * negative or wrapped long would corrupt every downstream calculation
     * (thread partitioning, progress percentages, ETA). We fail fast
     * instead, with a clear message telling the user to reduce maxLength
     * or charset size.
     */
    private static long exactPow(long base, int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result = Math.multiplyExact(result, base);
        }
        return result;
    }
}
