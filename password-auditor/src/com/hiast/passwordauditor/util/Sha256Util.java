package com.hiast.passwordauditor.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for SHA-256 hashing.
 *
 * IMPORTANT DESIGN NOTE (affects correctness under multithreading):
 * {@link MessageDigest} instances are NOT thread-safe. A single shared
 * MessageDigest object must never be used concurrently by multiple threads,
 * or the computed hashes will silently become incorrect (internal state
 * corruption), which is a classic hidden Race Condition.
 *
 * The correct pattern — used throughout this project — is for EACH worker
 * thread to obtain its OWN MessageDigest instance via {@link #newDigest()}
 * and reuse that single instance only within that thread.
 */
public final class Sha256Util {

    private static final String ALGORITHM = "SHA-256";

    private Sha256Util() {
        // Utility class: no instances.
    }

    /**
     * Creates a brand-new MessageDigest instance. Call this once per thread
     * (not once per hash) and reuse the returned object inside that thread's loop.
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present on every standard JVM,
            // so this branch should be unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm is not available on this JVM.", e);
        }
    }

    /**
     * Hashes a UTF-8 string using the given (thread-local) digest instance.
     * The digest is reset automatically by digest(), so it is safe to call
     * this repeatedly on the same MessageDigest instance from the same thread.
     */
    public static byte[] hash(MessageDigest digest, String input) {
        return digest.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convenience one-shot hashing method (creates its own digest).
     * Intended for single, infrequent calls (e.g. hashing the user's target
     * password once), NOT for use inside hot parallel loops.
     */
    public static byte[] hashOnce(String input) {
        return hash(newDigest(), input);
    }

    /** Converts raw hash bytes to a lowercase hex string for display/export. */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Constant-time-ish comparison (good practice for hash comparison). */
    public static boolean matches(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
