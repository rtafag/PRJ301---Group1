package com.group1.service;

import com.group1.model.Finding;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIService {

    private static final AIService INSTANCE = new AIService();

    public static AIService getInstance() {
        return INSTANCE;
    }

    private final Map<String, String> config = new HashMap<>();

    public AIService() {
        loadConfig();
    }

    private void loadConfig() {
        // 1. Try to load from portal-node/.env or .env if present
        File[] envFiles = new File[] { new File("portal-node/.env"), new File(".env"), new File("config.properties") };
        for (File f : envFiles) {
            if (f.exists() && f.isFile()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String key = line.substring(0, eq).trim();
                            String val = line.substring(eq + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            config.put(key, val);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Override with System environment variables if set
        String[] keys = { "AI_PROVIDER", "OPENAI_API_KEY", "OPENAI_MODEL", "GEMINI_API_KEY", "GEMINI_MODEL" };
        for (String k : keys) {
            String envVal = System.getenv(k);
            if (envVal != null && !envVal.trim().isEmpty()) {
                config.put(k, envVal.trim());
            }
        }
    }

    public String getProperty(String key, String def) {
        String val = config.get(key);
        return (val == null || val.trim().isEmpty()) ? def : val.trim();
    }

    public void setProperty(String key, String val) {
        config.put(key, val);
    }

    /**
     * Generates pedagogical AI feedback for code analysis findings.
     * Uses strict Anti-Hallucination prompt engineering.
     * If external API is disabled or fails, falls back gracefully to structured local Markdown explanation.
     */
    public String generateFeedback(String sourceCode, List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "## ✅ Kết quả đánh giá: Xuất sắc (10.0 / 10.0)\n\n" +
                   "Hệ thống phân tích AST không tìm thấy bất kỳ lỗi logic, vi phạm quy chuẩn hay rò rỉ tài nguyên nào. Mã nguồn tuân thủ tốt các quy chuẩn Clean Code của môn PRJ301.";
        }

        String provider = getProperty("AI_PROVIDER", "disabled").toLowerCase();

        // Check if OpenAI is enabled
        if ("openai".equals(provider)) {
            String apiKey = getProperty("OPENAI_API_KEY", "");
            if (!apiKey.isEmpty()) {
                try {
                    String feedback = callOpenAI(sourceCode, findings, apiKey);
                    if (feedback != null && !feedback.trim().isEmpty()) {
                        return feedback;
                    }
                } catch (Exception e) {
                    System.err.println("[AIService] OpenAI call failed: " + e.getMessage() + ". Falling back to local explanation.");
                }
            }
        }

        // Check if Gemini is enabled
        if ("gemini".equals(provider)) {
            String apiKey = getProperty("GEMINI_API_KEY", "");
            if (!apiKey.isEmpty()) {
                try {
                    String feedback = callGemini(sourceCode, findings, apiKey);
                    if (feedback != null && !feedback.trim().isEmpty()) {
                        return feedback;
                    }
                } catch (Exception e) {
                    System.err.println("[AIService] Gemini call failed: " + e.getMessage() + ". Falling back to local explanation.");
                }
            }
        }

        // Default: Intelligent structured local pedagogical explanation in Markdown
        return buildLocalExplanation(sourceCode, findings);
    }

    private String getSystemPrompt() {
        return "You are an expert Java Teaching Assistant in the AITA (AI Teaching Assistant) system for PRJ301.\n" +
               "Your objective is to provide professional, pedagogical code feedback in GitHub-flavored Markdown.\n" +
               "STRICT CONSTRAINTS (To eliminate hallucination & false positives):\n" +
               "1. Base your review ONLY on the exact AST violations and line numbers provided. Do NOT invent or assume non-existent syntax or compiler errors.\n" +
               "2. Do NOT alter, recalculate, or assign any numeric grades or penalties.\n" +
               "3. For EACH detected violation, structure your feedback clearly with:\n" +
               "   - **Root Cause & Runtime Impact**: Explain clearly why this is problematic (e.g. CPU thread exhaustion, identity vs value comparison, crash at runtime, unclosed DB connection pool exhaustion).\n" +
               "   - **Recommended Fix & Code Snippet**: Provide a concise before/after code snippet showing how to refactor correctly.\n" +
               "   - **Best Practice Tip**: A practical takeaway referencing Java or PRJ301 standards (e.g. Effective Java, SonarQube).\n" +
               "4. Use a constructive, pedagogical tone in Vietnamese for Vietnamese undergraduate students.";
    }

    private String callOpenAI(String sourceCode, List<Finding> findings, String apiKey) throws Exception {
        String model = getProperty("OPENAI_MODEL", "gpt-4o-mini");
        URL url = new URL("https://api.openai.com/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);

        String systemPrompt = getSystemPrompt();
        String userPrompt = buildPromptContext(sourceCode, findings);

        String payload = "{" +
                "\"model\":\"" + escapeJson(model) + "\"," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}" +
                "]," +
                "\"temperature\":0.2" +
                "}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            String err = readStream(conn.getErrorStream());
            throw new RuntimeException("HTTP " + status + ": " + err);
        }

        String response = readStream(conn.getInputStream());
        return extractOpenAIContent(response);
    }

    private String callGemini(String sourceCode, List<Finding> findings, String apiKey) throws Exception {
        String model = getProperty("GEMINI_MODEL", "gemini-2.0-flash");
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);

        String systemPrompt = getSystemPrompt();
        String userPrompt = buildPromptContext(sourceCode, findings);

        String payload = "{" +
                "\"systemInstruction\":{\"parts\":[{\"text\":\"" + escapeJson(systemPrompt) + "\"}]}," +
                "\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(userPrompt) + "\"}]}]" +
                "}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            String err = readStream(conn.getErrorStream());
            throw new RuntimeException("HTTP " + status + ": " + err);
        }

        String response = readStream(conn.getInputStream());
        return extractGeminiContent(response);
    }

    public String buildPromptContext(String sourceCode, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("Danh sách vi phạm do AST Rule Engine phát hiện (").append(findings.size()).append(" lỗi):\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append(i + 1).append(". [").append(f.rule.ruleCode).append("] ")
              .append(f.rule.ruleName).append(" (Dòng ").append(f.line)
              .append(", Mức độ: ").append(f.rule.severity)
              .append(", Trừ: ").append(f.rule.penaltyPoints).append("đ): ")
              .append(f.suggestion).append("\n");
        }
        sb.append("\nTrích đoạn mã nguồn ngữ cảnh xung quanh dòng bị lỗi:\n");
        String[] lines = sourceCode.split("\n");
        for (Finding f : findings) {
            int start = Math.max(0, f.line - 3);
            int end = Math.min(lines.length - 1, f.line + 2);
            sb.append("--- Ngữ cảnh Dòng ").append(f.line).append(" (Rule: ").append(f.rule.ruleCode).append(") ---\n");
            for (int l = start; l <= end; l++) {
                sb.append(l + 1 == f.line ? ">> " : "   ")
                  .append(l + 1).append(": ").append(lines[l]).append("\n");
            }
        }
        return sb.toString();
    }

    public String buildLocalExplanation(String sourceCode, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🎓 Báo cáo Hướng dẫn & Diễn giải Chi tiết (AITA AI Feedback)\n\n");
        sb.append("> Hệ thống phân tích tĩnh AST đã phát hiện **").append(findings.size())
          .append(" vấn đề** trong bài nộp. Dưới đây là phân tích chi tiết và giải pháp khắc phục chuẩn:\n\n");

        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("### ").append(i + 1).append(". [").append(f.rule.ruleCode).append("] ")
              .append(f.rule.ruleName).append(" (Dòng ").append(f.line).append(")\n");
            sb.append("- **Mức độ nghiêm trọng:** `").append(f.rule.severity).append("` (Khấu trừ: -")
              .append(f.rule.penaltyPoints).append(" điểm)\n");

            sb.append("- **Nguyên nhân & Nguy cơ:** ");
            sb.append(getPedagogicalRisk(f.rule.ruleCode)).append("\n");

            sb.append("- **Gợi ý khắc phục:** ").append(f.suggestion).append("\n");

            String snippet = getCodeSampleForFix(f.rule.ruleCode);
            if (!snippet.isEmpty()) {
                sb.append("- **Mẫu code sửa chuẩn:**\n```java\n").append(snippet).append("\n```\n");
            }
            sb.append("\n");
        }
        sb.append("---\n*Lời khuyên từ AITA:* Hãy rà soát lại các điểm cảnh báo trên trước khi nộp bài bảo vệ để đảm bảo mã nguồn đạt chuẩn Clean Code và không gặp sự cố runtime!");
        return sb.toString().trim();
    }

    private String getPedagogicalRisk(String ruleCode) {
        switch (ruleCode) {
            case "INF_LOOP":
                return "Vòng lặp không có điều kiện thoát sẽ chiếm dụng 100% CPU thread, gây đóng băng máy chủ Servlet và nghẽn toàn bộ hệ thống.";
            case "STR_EQ":
                return "Toán tử '==' chỉ so sánh địa chỉ tham chiếu (vùng nhớ), không so sánh nội dung chuỗi. Khi so sánh dữ liệu nhập vào sẽ luôn trả về false.";
            case "EMPTY_CATCH":
                return "Khối catch rỗng nuốt chửng ngoại lệ (Swallowed Exception), khiến lỗi runtime không được ghi nhận và ứng dụng chết âm thầm khó debug.";
            case "RES_LEAK":
                return "Mở Connection/PreparedStatement mà không đóng sẽ gây rò rỉ tài nguyên, làm cạn kiệt Connection Pool của SQL Server.";
            case "DIV_ZERO":
                return "Phép chia cho số 0 gây ngoại lệ ArithmeticException nghiêm trọng ngay trong thời gian thực thi (Runtime Crash).";
            case "ASSIGN_IN_COND":
                return "Dùng phép gán '=' trong biểu thức điều kiện làm thay đổi giá trị biến ngoài ý muốn thay vì so sánh.";
            case "DEAD_CODE":
                return "Mã nằm sau return/throw không bao giờ được chạm tới, thể hiện logic bị lỗi hoặc code thừa thãi.";
            case "MISSING_OVERRIDE":
                return "Thiếu @Override trên các hàm tiêu chuẩn (doGet, doPost, equals, toString) dễ dẫn đến lỗi gõ sai chữ hoa/thường biến thành method overload mới.";
            case "EQUALS_HASHCODE":
                return "Ghi đè equals mà quên hashCode vi phạm hợp đồng Object của Java, khiến việc lưu/tìm trong HashMap, HashSet bị sai kết quả.";
            case "NPE":
                return "Hàm trả về null trực tiếp mà không có kiểm tra dễ dẫn đến lỗi NullPointerException cho các module gọi hàm.";
            case "OOM":
                return "Khởi tạo mảng mới liên tục trong vòng lặp tiêu tốn nhiều bộ nhớ Heap, có thể gây lỗi OutOfMemoryError.";
            case "CS":
                return "Tên class/interface không tuân thủ quy tắc viết hoa chữ cái đầu (PascalCase) trong Java Convention.";
            case "LONG_METHOD":
                return "Method quá dài (>40 dòng) vi phạm nguyên lý Single Responsibility, gây khó khăn cho việc bảo trì.";
            case "COMMENT":
                return "Method public thiếu Javadoc chú thích mục đích hàm, các tham số đầu vào và giá trị trả về.";
            default:
                return "Vi phạm quy chuẩn chất lượng mã nguồn Java.";
        }
    }

    private String getCodeSampleForFix(String ruleCode) {
        switch (ruleCode) {
            case "INF_LOOP":
                return "// Sửa: Thêm biến điều kiện hoặc lệnh break\nwhile (isRunning) {\n    if (hasCompleted()) break;\n    processData();\n}";
            case "STR_EQ":
                return "// Sửa: Dùng .equals() hoặc Objects.equals()\nif (\"admin\".equals(role)) {\n    grantAccess();\n}";
            case "EMPTY_CATCH":
                return "// Sửa: Ít nhất phải log lỗi hoặc ném tiếp ngoại lệ\n} catch (SQLException e) {\n    logger.log(Level.SEVERE, \"Database query failed\", e);\n    throw new ServletException(e);\n}";
            case "RES_LEAK":
                return "// Sửa: Dùng try-with-resources để tự động đóng kết nối\ntry (Connection conn = DBConnection.getConnection();\n     PreparedStatement ps = conn.prepareStatement(sql)) {\n    // Thao tác DB an toàn...\n}";
            case "DIV_ZERO":
                return "// Sửa: Thêm guard clause kiểm tra mẫu số khác 0\nif (divisor != 0) {\n    int result = total / divisor;\n}";
            case "ASSIGN_IN_COND":
                return "// Sửa: Dùng toán tử so sánh == thay vì phép gán =\nif (status == 1) {\n    // ...\n}";
            case "MISSING_OVERRIDE":
                return "// Sửa: Thêm annotation @Override\n@Override\npublic String toString() {\n    return \"User: \" + username;\n}";
            case "EQUALS_HASHCODE":
                return "// Sửa: Cùng override cả equals và hashCode\n@Override\npublic boolean equals(Object o) { ... }\n\n@Override\npublic int hashCode() { return Objects.hash(id); }";
            default:
                return "";
        }
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String extractOpenAIContent(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx == -1) return json;
        int start = json.indexOf('"', idx + 10);
        if (start == -1) return json;
        int end = findJsonStringEnd(json, start + 1);
        return unescapeJson(json.substring(start + 1, end));
    }

    private String extractGeminiContent(String json) {
        int idx = json.indexOf("\"text\":");
        if (idx == -1) return json;
        int start = json.indexOf('"', idx + 7);
        if (start == -1) return json;
        int end = findJsonStringEnd(json, start + 1);
        return unescapeJson(json.substring(start + 1, end));
    }

    private int findJsonStringEnd(String s, int start) {
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return s.length();
    }

    public static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '/': sb.append('/'); i += 2; break;
                    case 'b': sb.append('\b'); i += 2; break;
                    case 'f': sb.append('\f'); i += 2; break;
                    case 'n': sb.append('\n'); i += 2; break;
                    case 'r': sb.append('\r'); i += 2; break;
                    case 't': sb.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                            } catch (Exception e) {
                                sb.append(c);
                                i++;
                            }
                        } else {
                            sb.append(c);
                            i++;
                        }
                        break;
                    default:
                        sb.append(c);
                        i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
