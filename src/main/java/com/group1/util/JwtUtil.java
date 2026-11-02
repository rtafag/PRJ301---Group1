package com.group1.util;

import com.group1.model.Users;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Standard RFC 7519 JWT (JSON Web Token) Utility for AITA Authentication.
 * Uses HMAC-SHA256 (HS256) signature, interoperable with portal-node.
 */
public class JwtUtil {
    private static final String DEFAULT_SECRET = "aita-secret-key-prj301-group1-2026";
    private static final long EXPIRATION_TIME_MS = 8 * 60 * 60 * 1000L; // 8 hours

    public static class Claims {
        public String sub;
        public String userId;
        public String username;
        public String role;
        public String fullName;
        public long exp;

        @Override
        public String toString() {
            return "Claims{userId='" + userId + "', username='" + username + "', role='" + role + "'}";
        }
    }

    private static String getSecret() {
        return System.getenv().getOrDefault("JWT_SECRET", DEFAULT_SECRET);
    }

    public static String generateToken(Users user) {
        long now = System.currentTimeMillis() / 1000;
        long exp = now + (EXPIRATION_TIME_MS / 1000);

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(
                "{\"sub\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}",
                escape(user.userId),
                escape(user.userId),
                escape(user.username),
                escape(user.fullName),
                escape(user.role),
                now,
                exp
        );

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String data = encodedHeader + "." + encodedPayload;
        String signature = sign(data, getSecret());

        return data + "." + signature;
    }

    public static boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        return claims.exp > now;
    }

    public static Claims parseToken(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String data = parts[0] + "." + parts[1];
        String expectedSig = sign(data, getSecret());
        if (!expectedSig.equals(parts[2])) {
            return null; // Invalid signature
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);

            Claims claims = new Claims();
            claims.sub = extractJsonField(payloadJson, "sub");
            claims.userId = extractJsonField(payloadJson, "userId");
            claims.username = extractJsonField(payloadJson, "username");
            claims.role = extractJsonField(payloadJson, "role");
            claims.fullName = extractJsonField(payloadJson, "fullName");
            String expStr = extractJsonField(payloadJson, "exp");
            claims.exp = expStr != null ? Long.parseLong(expStr) : 0;
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\":";
        int index = json.indexOf(pattern);
        if (index == -1) return null;
        int start = index + pattern.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            return end != -1 ? json.substring(start, end) : null;
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            return json.substring(start, end);
        }
    }

    public static void main(String[] args) {
        Users u = new Users("HE001", "dungdang442", "hash_dungdang442", "Đặng Quang Dũng", "dung@gmail.com", "Student", "2023-10-01");
        String token = generateToken(u);
        System.out.println("Generated JWT: " + token);
        boolean valid = validateToken(token);
        System.out.println("Validated: " + valid);
        Claims claims = parseToken(token);
        System.out.println("Parsed Claims: " + claims);
    }
}
