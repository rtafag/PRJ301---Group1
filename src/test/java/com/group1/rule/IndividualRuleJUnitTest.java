package com.group1.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.group1.model.AnalysisOutcome;
import com.group1.model.Finding;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Bộ kiểm thử JUnit 4 cho từng Rule AST độc lập (TV3).
 * Xác minh 15/15 Rule Visitors của ASTRuleEngine phát hiện chính xác lỗi (Recall = 100%).
 */
public class IndividualRuleJUnitTest {

    private static ASTRuleEngine engine;

    @BeforeClass
    public static void setUp() {
        engine = new ASTRuleEngine();
    }

    private Finding findRule(AnalysisOutcome outcome, String ruleCode) {
        for (Finding item : outcome.findings) {
            if (ruleCode.equalsIgnoreCase(item.rule.ruleCode)) {
                return item;
            }
        }
        return null;
    }

    @Test
    public void testRule01_InfiniteLoop() {
        String code = "public class BadLoop {\n" +
                      "    public void run() {\n" +
                      "        while (true) {\n" +
                      "            System.out.println(\"running\");\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "INF_LOOP");
        assertNotNull("Rule INF_LOOP must detect while(true) infinite loop", f);
        assertTrue("Penalty must be at least 10.0 points", f.rule.penaltyPoints >= 10.0f);
    }

    @Test
    public void testRule02_StringComparison() {
        String code = "public class BadString {\n" +
                      "    public boolean check(String user) {\n" +
                      "        if (user == \"admin\") {\n" +
                      "            return true;\n" +
                      "        }\n" +
                      "        return false;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "STR_EQ");
        assertNotNull("Rule STR_EQ must detect string comparison using ==", f);
        assertEquals("STR_EQ", f.rule.ruleCode);
    }

    @Test
    public void testRule03_DivisionByZero() {
        String code = "public class BadMath {\n" +
                      "    public int div(int a) {\n" +
                      "        return a / 0;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "DIV_ZERO");
        assertNotNull("Rule DIV_ZERO must detect division by constant zero", f);
        assertEquals("DIV_ZERO", f.rule.ruleCode);
    }

    @Test
    public void testRule04_AssignmentInCondition() {
        String code = "public class BadAssign {\n" +
                      "    public void check(boolean a, boolean b) {\n" +
                      "        if (a = b) {\n" +
                      "            System.out.println(\"bad\");\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "ASSIGN_IN_COND");
        assertNotNull("Rule ASSIGN_IN_COND must detect assignment inside if statement", f);
        assertEquals("ASSIGN_IN_COND", f.rule.ruleCode);
    }

    @Test
    public void testRule05_DeadCode() {
        String code = "public class BadDead {\n" +
                      "    public int calc() {\n" +
                      "        return 42;\n" +
                      "        int unreachable = 100;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "DEAD_CODE");
        assertNotNull("Rule DEAD_CODE must detect unreachable code after return", f);
        assertEquals("DEAD_CODE", f.rule.ruleCode);
    }

    @Test
    public void testRule06_MissingOverride() {
        String code = "public class BadOverride {\n" +
                      "    public String toString() {\n" +
                      "        return \"missing @Override\";\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "MISSING_OVERRIDE");
        assertNotNull("Rule MISSING_OVERRIDE must detect standard method lacking @Override", f);
        assertEquals("MISSING_OVERRIDE", f.rule.ruleCode);
    }

    @Test
    public void testRule07_EqualsWithoutHashCode() {
        String code = "public class BadEquals {\n" +
                      "    public boolean equals(Object o) {\n" +
                      "        return true;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "EQUALS_HASHCODE");
        assertNotNull("Rule EQUALS_HASHCODE must detect equals() overridden without hashCode()", f);
        assertEquals("EQUALS_HASHCODE", f.rule.ruleCode);
    }

    @Test
    public void testRule08_DuplicateCode() {
        String code = "public class BadDup {\n" +
                      "    public void m1() {\n" +
                      "        System.out.println(\"duplicate code block statement A\");\n" +
                      "        System.out.println(\"duplicate code block statement B\");\n" +
                      "        System.out.println(\"duplicate code block statement C\");\n" +
                      "    }\n" +
                      "    public void m2() {\n" +
                      "        System.out.println(\"duplicate code block statement A\");\n" +
                      "        System.out.println(\"duplicate code block statement B\");\n" +
                      "        System.out.println(\"duplicate code block statement C\");\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "DUPLICATE_CODE");
        assertNotNull("Rule DUPLICATE_CODE must detect identical method bodies", f);
        assertEquals("DUPLICATE_CODE", f.rule.ruleCode);
    }

    @Test
    public void testRule09_ResourceLeak() {
        String code = "import java.sql.Connection;\n" +
                      "import java.sql.DriverManager;\n" +
                      "import java.sql.SQLException;\n" +
                      "public class BadLeak {\n" +
                      "    public void query() throws SQLException {\n" +
                      "        Connection conn = DriverManager.getConnection(\"jdbc:sqlserver://localhost\");\n" +
                      "        // forget to close conn\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "RES_LEAK");
        assertNotNull("Rule RES_LEAK must detect unclosed JDBC Connection", f);
        assertEquals("RES_LEAK", f.rule.ruleCode);
    }

    @Test
    public void testRule10_EmptyCatch() {
        String code = "public class BadCatch {\n" +
                      "    public void run() {\n" +
                      "        try {\n" +
                      "            int x = 1 / 0;\n" +
                      "        } catch (Exception e) {\n" +
                      "            // empty catch swallowing error\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "EMPTY_CATCH");
        assertNotNull("Rule EMPTY_CATCH must detect empty catch block", f);
        assertEquals("EMPTY_CATCH", f.rule.ruleCode);
    }

    @Test
    public void testRule11_NullReturn() {
        String code = "public class BadNull {\n" +
                      "    public String getName() {\n" +
                      "        return null;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "NPE");
        assertNotNull("Rule NPE must detect return null", f);
        assertEquals("NPE", f.rule.ruleCode);
    }

    @Test
    public void testRule12_MemoryLeakInLoop() {
        String code = "public class BadMem {\n" +
                      "    public void run() {\n" +
                      "        for (int i = 0; i < 100; i++) {\n" +
                      "            byte[] buffer = new byte[1024 * 1024];\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "OOM");
        assertNotNull("Rule OOM must detect array allocation in loop", f);
        assertEquals("OOM", f.rule.ruleCode);
    }

    @Test
    public void testRule13_NamingConvention() {
        String code = "public class badClassName {\n" +
                      "    public void run() {}\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "CS");
        assertNotNull("Rule CS must detect class name starting with lowercase letter", f);
        assertEquals("CS", f.rule.ruleCode);
    }

    @Test
    public void testRule14_LongMethod() {
        StringBuilder sb = new StringBuilder();
        sb.append("public class BadLongMethod {\n");
        sb.append("    public int huge() {\n");
        sb.append("        int x = 0;\n");
        for (int i = 0; i < 45; i++) {
            sb.append("        x += ").append(i).append(";\n");
        }
        sb.append("        return x;\n");
        sb.append("    }\n");
        sb.append("}");
        AnalysisOutcome res = engine.analyze(sb.toString());
        Finding f = findRule(res, "LONG_METHOD");
        assertNotNull("Rule LONG_METHOD must detect method longer than 40 lines", f);
        assertEquals("LONG_METHOD", f.rule.ruleCode);
    }

    @Test
    public void testRule15_MissingComment() {
        String code = "public class BadComment {\n" +
                      "    public void doSomethingWithoutJavadoc() {}\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        Finding f = findRule(res, "COMMENT");
        assertNotNull("Rule COMMENT must detect public method lacking Javadoc comment", f);
        assertEquals("COMMENT", f.rule.ruleCode);
    }
}
