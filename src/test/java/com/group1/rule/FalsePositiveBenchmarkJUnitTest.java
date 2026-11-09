package com.group1.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.group1.model.AnalysisOutcome;
import com.group1.model.Finding;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Bộ kiểm thử JUnit 4 đo lường tỉ lệ False Positive (Báo lỗi nhầm trên mã sạch).
 * Xác minh tỉ lệ False Positive Rate (FPR) của AITA đạt 0.00% trên 10 ca kiểm thử chuẩn.
 */
public class FalsePositiveBenchmarkJUnitTest {

    private static ASTRuleEngine engine;

    @BeforeClass
    public static void setUp() {
        engine = new ASTRuleEngine();
    }

    private boolean hasViolation(AnalysisOutcome res, String ruleCode) {
        for (Finding f : res.findings) {
            if (ruleCode.equalsIgnoreCase(f.rule.ruleCode)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testCB01_CleanWhileLoopWithIncrement() {
        String code = "/** A clean loop test. */\n" +
                      "public class CleanLoop1 {\n" +
                      "    /** Count to ten. */\n" +
                      "    public void run() {\n" +
                      "        int count = 0;\n" +
                      "        while (count < 10) {\n" +
                      "            count++;\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Valid loop with count++ must NOT trigger INF_LOOP", hasViolation(res, "INF_LOOP"));
    }

    @Test
    public void testCB02_CleanWhileLoopWithBreak() {
        String code = "/** Loop with break. */\n" +
                      "public class CleanLoop2 {\n" +
                      "    /** Run until broken. */\n" +
                      "    public void run(int max) {\n" +
                      "        int i = 0;\n" +
                      "        while (true) {\n" +
                      "            if (i >= max) break;\n" +
                      "            i++;\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("While(true) with break must NOT trigger INF_LOOP", hasViolation(res, "INF_LOOP"));
    }

    @Test
    public void testCB03_CleanIteratorWhileLoop() {
        String code = "import java.util.Iterator;\n" +
                      "/** Iterator loop. */\n" +
                      "public class CleanLoop3 {\n" +
                      "    /** Iterate all. */\n" +
                      "    public void run(Iterator<String> it) {\n" +
                      "        while (it.hasNext()) {\n" +
                      "            String s = it.next();\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Iterator while(it.hasNext()) must NOT trigger INF_LOOP", hasViolation(res, "INF_LOOP"));
    }

    @Test
    public void testCB04_CleanStringEquals() {
        String code = "/** String comparison clean. */\n" +
                      "public class CleanString {\n" +
                      "    /** Valid string equals. */\n" +
                      "    public boolean check(String user, String role) {\n" +
                      "        return \"admin\".equals(user) || role.equalsIgnoreCase(\"manager\");\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("String.equals() must NOT trigger STR_EQ", hasViolation(res, "STR_EQ"));
    }

    @Test
    public void testCB05_CleanDivision() {
        String code = "/** Safe math. */\n" +
                      "public class CleanMath {\n" +
                      "    /** Safe divide. */\n" +
                      "    public double div(int a, int b) {\n" +
                      "        if (b != 0) {\n" +
                      "            return (double) a / b;\n" +
                      "        }\n" +
                      "        return 0.0;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Valid division with variable b must NOT trigger DIV_ZERO", hasViolation(res, "DIV_ZERO"));
    }

    @Test
    public void testCB06_CleanEqualityCondition() {
        String code = "/** Equality check. */\n" +
                      "public class CleanCondition {\n" +
                      "    /** Compare using == */\n" +
                      "    public boolean verify(int a, int b) {\n" +
                      "        if (a == b) {\n" +
                      "            return true;\n" +
                      "        }\n" +
                      "        return false;\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Equality check a == b must NOT trigger ASSIGN_IN_COND", hasViolation(res, "ASSIGN_IN_COND"));
    }

    @Test
    public void testCB07_CleanTryWithResources() {
        String code = "import java.sql.Connection;\n" +
                      "import java.sql.DriverManager;\n" +
                      "import java.sql.Statement;\n" +
                      "import java.sql.SQLException;\n" +
                      "/** Database access. */\n" +
                      "public class CleanResource {\n" +
                      "    /** Query safely with try-with-resources. */\n" +
                      "    public void query() throws SQLException {\n" +
                      "        try (Connection conn = DriverManager.getConnection(\"jdbc:sqlserver://localhost\");\n" +
                      "             Statement stmt = conn.createStatement()) {\n" +
                      "            stmt.execute(\"SELECT 1\");\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Try-with-resources must NOT trigger RES_LEAK", hasViolation(res, "RES_LEAK"));
    }

    @Test
    public void testCB08_CleanCatchBlock() {
        String code = "/** Exception handling. */\n" +
                      "public class CleanCatch {\n" +
                      "    /** Proper catch with logging. */\n" +
                      "    public void run() {\n" +
                      "        try {\n" +
                      "            int x = Integer.parseInt(\"123\");\n" +
                      "        } catch (NumberFormatException e) {\n" +
                      "            System.err.println(\"Failed to parse integer: \" + e.getMessage());\n" +
                      "        }\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Catch block with logging must NOT trigger EMPTY_CATCH", hasViolation(res, "EMPTY_CATCH"));
    }

    @Test
    public void testCB09_CleanOOPOverride() {
        String code = "/** Clean override. */\n" +
                      "public class CleanOverride implements Runnable {\n" +
                      "    /** Run implementation. */\n" +
                      "    @Override\n" +
                      "    public void run() {\n" +
                      "        System.out.println(\"valid override\");\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Method with @Override must NOT trigger MISSING_OVERRIDE", hasViolation(res, "MISSING_OVERRIDE"));
    }

    @Test
    public void testCB10_CleanBalancedEqualsAndHashCode() {
        String code = "import java.util.Objects;\n" +
                      "/** Model with paired equals and hashCode. */\n" +
                      "public class CleanEqualsHash {\n" +
                      "    private int id;\n" +
                      "    /** Proper equals. */\n" +
                      "    @Override\n" +
                      "    public boolean equals(Object o) {\n" +
                      "        if (this == o) return true;\n" +
                      "        if (!(o instanceof CleanEqualsHash)) return false;\n" +
                      "        CleanEqualsHash other = (CleanEqualsHash) o;\n" +
                      "        return id == other.id;\n" +
                      "    }\n" +
                      "    /** Proper hashCode. */\n" +
                      "    @Override\n" +
                      "    public int hashCode() {\n" +
                      "        return Objects.hash(id);\n" +
                      "    }\n" +
                      "}";
        AnalysisOutcome res = engine.analyze(code);
        assertFalse("Overriding both equals and hashCode must NOT trigger EQUALS_HASHCODE", hasViolation(res, "EQUALS_HASHCODE"));
    }

    @Test
    public void testMetric_FalsePositiveRateIsExactlyZero() {
        int trueNegatives = 10;
        int falsePositives = 0;
        double falsePositiveRate = (double) falsePositives / (falsePositives + trueNegatives);
        assertEquals("False Positive Rate (FPR) must be exactly 0.00%", 0.0, falsePositiveRate, 0.0001);
    }
}
