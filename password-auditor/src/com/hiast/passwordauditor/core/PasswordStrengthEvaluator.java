package com.hiast.passwordauditor.core;

/**
 * Converts a raw "time to brute-force this password" figure into a
 * human-readable strength verdict. This is what turns the project from a
 * pure threading benchmark into the practical application the assignment
 * requires ("أداة لاختبار قوة كلمات المرور").
 *
 * The thresholds below are configuration constants (named, documented, and
 * easy to change in one place) rather than unexplained magic numbers
 * scattered through the code.
 */
public final class PasswordStrengthEvaluator {

    public enum Rating {
        WEAK("Weak — crackable almost instantly"),
        MODERATE("Moderate — crackable within hours to days"),
        STRONG("Strong — would take years to brute-force"),
        VERY_STRONG("Very strong — practically infeasible to brute-force");

        public final String description;

        Rating(String description) {
            this.description = description;
        }
    }

    // Boundaries, in seconds, between rating tiers. Chosen to be easily
    // tunable in one place; see README for the reasoning behind each value.
    private static final double WEAK_UPPER_BOUND_SECONDS = 60;                  // < 1 minute
    private static final double MODERATE_UPPER_BOUND_SECONDS = 60 * 60 * 24;    // < 1 day
    private static final double STRONG_UPPER_BOUND_SECONDS = 60L * 60 * 24 * 365; // < 1 year

    private PasswordStrengthEvaluator() {
        // Utility class: no instances.
    }

    /**
     * Estimates how long an attacker with the given measured throughput
     * would need, on average, to brute-force a password drawn from the
     * given candidate space (average case: half the full space).
     */
    public static double estimateAverageCrackTimeSeconds(CandidateSpace attackerSearchSpace,
                                                           double attackerHashesPerSecond) {
        if (attackerHashesPerSecond <= 0) {
            throw new IllegalArgumentException("attackerHashesPerSecond must be > 0.");
        }
        double worstCaseSeconds = attackerSearchSpace.totalCandidates() / attackerHashesPerSecond;
        return worstCaseSeconds / 2.0; // average case
    }

    public static Rating rate(double averageCrackTimeSeconds) {
        if (averageCrackTimeSeconds < WEAK_UPPER_BOUND_SECONDS) {
            return Rating.WEAK;
        } else if (averageCrackTimeSeconds < MODERATE_UPPER_BOUND_SECONDS) {
            return Rating.MODERATE;
        } else if (averageCrackTimeSeconds < STRONG_UPPER_BOUND_SECONDS) {
            return Rating.STRONG;
        } else {
            return Rating.VERY_STRONG;
        }
    }
}
