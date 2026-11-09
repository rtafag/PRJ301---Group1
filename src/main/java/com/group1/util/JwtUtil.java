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

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getFullName() { return fullName; }
        public long getExp() { return exp; }

        @Override
        public String toString() {
            return "Claims{userId='" + userId + "', username='" + username + "', role='" + role + "'}";
        }
    }

    private static String getSecret() {
        return System.getenv().getOrDefault("JWT_SECRET", DEFAULT_SECRET);
    }

    public static String generateToken(Users user) {
        return generateToken(user.getUserId(), user.getUsername(), user.getRole(), user.getFullName());
    }

    public static String generateToken(String userId, String username, String role) {
        return generateToken(userId, username, role, username);
    }

    public static String generateToken(String userId, String username, String role, String fullName) {
        long now = System.currentTimeMillis() / 1000;
        long exp = now + (EXPIRATION_TIME_MS / 1000);

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(
                "{\"sub\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}",
                escape(userId),
                escape(userId),
                escape(username),
                escape(fullName != null ? fullName : username),
                escape(role),
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

    public static Claims getClaims(String token) {
        return parseToken(token);
    }

    public static Claims parseToken(String token) {
        if (token == null || !token.contains(".")) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String data = parts[0] + "." + parts[1];
        String expectedSignature = sign(data, getSecret());

        if (!parts[2].equals(expectedSignature)) {
            return null; // Invalid signature
        }

        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payloadJson = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);

            Claims claims = new Claims();
            claims.sub = extractField(payloadJson, "sub");
            claims.userId = extractField(payloadJson, "userId");
            claims.username = extractField(payloadJson, "username");
            claims.role = extractField(payloadJson, "role");
            claims.fullName = extractField(payloadJson, "fullName");

            String expStr = extractNumericField(payloadJson, "exp");
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

    private static String escape(String val) {
        if (val == null) return "";
        return val.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String extractField(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int idx = json.indexOf(marker);
        if (idx == -1) return null;
        int start = idx + marker.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : null;
    }

    private static String extractNumericField(String json, String key) {
        String marker = "\"" + key + "\":";
        int idx = json.indexOf(marker);
        if (idx == -1) return null;
        int start = idx + marker.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return json.substring(start, end).trim();
    }

    public static void main(String[] args) {
        Users dummy = new Users("HE001", "dungdang442", "dummyHash", "Đặng Quang Dũng", "dung@fpt.edu.vn", "Student", "2023-10-01");
        String token = generateToken(dummy);
        System.out.println("Generated JWT: " + token);
        System.out.println("Validated: " + validateToken(token));
        System.out.println("Parsed Claims: " + parseToken(token));
    }
}
