package com.songhg.veri.agent.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TokenSecurity {

    private TokenSecurity() {
    }

    public static boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedHash = sha256(expected == null ? "" : expected);
        byte[] actualHash = sha256(actual == null ? "" : actual);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
