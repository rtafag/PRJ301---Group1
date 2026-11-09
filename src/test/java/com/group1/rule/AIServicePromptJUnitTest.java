package com.group1.rule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.group1.model.Finding;
import com.group1.model.RuleDefinition;
import com.group1.service.AIService;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Bộ kiểm thử JUnit 4 cho Prompt Engineering và Context Slicing (TV3).
 * Xác minh:
 * 1. Prompt TUYỆT ĐỐI KHÔNG gửi toàn bộ mã nguồn thô (raw source code).
 * 2. Context Slicing chỉ trích xuất 5 dòng quanh vị trí lỗi và giảm thiểu dung lượng token.
 * 3. Local Pedagogical Fallback định dạng Markdown chuẩn sư phạm có Before/After code snippet.
 */
public class AIServicePromptJUnitTest {

    @Test
    public void testPromptDoesNotSendRawSourceCode() {
        AIService ai = AIService.getInstance();

        // Xây dựng một file mã nguồn lớn 120 dòng với lỗi logic ở dòng 60
        StringBuilder sb = new StringBuilder();
        sb.append("package com.test;\n");
        sb.append("public class VeryLargeFile {\n");
        for (int i = 3; i < 60; i++) {
            sb.append("    private int dummyField").append(i).append(" = ").append(i).append(";\n");
        }
        sb.append("    public int buggyMethod(int a) {\n"); // line 60
        sb.append("        return a / 0;\n"); // line 61: division by zero
        sb.append("    }\n");
        for (int i = 63; i <= 120; i++) {
            sb.append("    public void unusedTrailingMethod").append(i).append("() {}\n");
        }
        sb.append("}\n");

        String rawSource = sb.toString();

        RuleDefinition divZeroRule = new RuleDefinition(3, "DIV_ZERO", "Division by zero", "Error", 3.0f, "Cannot divide by 0");
        Finding finding = new Finding(divZeroRule, 61, "Tránh chia cho hằng số 0");

        List<Finding> findings = new ArrayList<>();
        findings.add(finding);

        // Sinh context trích đoạn
        String promptPayload = ai.buildPromptContext(rawSource, findings);
        String slicedContext = ai.extractSlicedContext(rawSource, findings);

        // 1. Phải chứa đúng dòng lỗi với marker >>
        assertTrue("Prompt context must contain marker >> on line 61", promptPayload.contains(">> 61:         return a / 0;"));

        // 2. Phải chứa các dòng ngữ cảnh lân cận (dòng 60 - 62)
        assertTrue("Prompt context must contain nearby line 60", promptPayload.contains("60:"));
        assertTrue("Prompt context must contain nearby line 62", promptPayload.contains("62:"));

        // 3. TUYỆT ĐỐI KHÔNG chứa các vùng mã nguồn ở xa (dòng 10 hay dòng 100)
        assertFalse("Prompt must NOT contain raw source line 10", promptPayload.contains("dummyField10"));
        assertFalse("Prompt must NOT contain raw source line 25", promptPayload.contains("dummyField25"));
        assertFalse("Prompt must NOT contain raw source line 100", promptPayload.contains("unusedTrailingMethod100"));

        // 4. Kích thước trích đoạn ngữ cảnh phải nhỏ hơn 25% kích thước file thô
        assertTrue("Context slicing must reduce size to less than 25% of raw source",
                slicedContext.length() < rawSource.length() * 0.25);
    }

    @Test
    public void testLocalPedagogicalFallbackMarkdownStructure() {
        AIService ai = AIService.getInstance();

        String code = "public class Sample { public void f() { while(true) {} } }";
        RuleDefinition loopRule = new RuleDefinition(1, "INF_LOOP", "Infinite Loop", "Error", 10.0f, "Loop has no exit condition");
        Finding finding = new Finding(loopRule, 1, "Thêm điều kiện break hoặc bước nhảy cho vòng lặp");

        List<Finding> findings = new ArrayList<>();
        findings.add(finding);

        String markdown = ai.buildLocalExplanation(code, findings);

        assertNotNull("Feedback cannot be null", markdown);
        assertTrue("Must contain Markdown heading", markdown.contains("## 🎓 Báo cáo Hướng dẫn & Diễn giải Chi tiết"));
        assertTrue("Must specify severity", markdown.contains("- **Mức độ nghiêm trọng:**"));
        assertTrue("Must explain root cause and risk", markdown.contains("- **Nguyên nhân & Nguy cơ:**"));
        assertTrue("Must provide fix suggestion", markdown.contains("- **Gợi ý khắc phục:**"));
        assertTrue("Must include code snippet block", markdown.contains("```java"));
    }

    @Test
    public void testContextSlicingReducesCharacterCount() {
        AIService ai = AIService.getInstance();

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            sb.append("int line").append(i).append(" = ").append(i).append(";\n");
        }
        String source = sb.toString();

        RuleDefinition rule = new RuleDefinition(11, "NPE", "Null return", "Warning", 2.0f, "Avoid returning null");
        Finding f = new Finding(rule, 100, "Return Optional or empty collection");
        List<Finding> findings = new ArrayList<>();
        findings.add(f);

        String sliced = ai.extractSlicedContext(source, findings);

        assertTrue("Original source has more than 2000 chars", source.length() > 2000);
        assertTrue("Sliced context is under 400 chars", sliced.length() < 400);
    }
}
