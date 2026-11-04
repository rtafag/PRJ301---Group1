package com.group1.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import javax.servlet.http.HttpSession;

public final class WebSecurity {
    public static final String SESSION_USER = "sessionUser";
    public static final String CSRF_TOKEN = "csrfToken";

    private WebSecurity() {
    }

    public static String getOrCreateCsrfToken(HttpSession session) {
        String token = (String) session.getAttribute(CSRF_TOKEN);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(CSRF_TOKEN, token);
        }
        return token;
    }

    public static boolean isValidCsrfToken(HttpSession session, String candidate) {
        if (session == null || candidate == null) return false;
        Object expected = session.getAttribute(CSRF_TOKEN);
        if (!(expected instanceof String)) return false;
        return MessageDigest.isEqual(
                ((String) expected).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
