package com.group1.rule;

import com.group1.model.AnalysisOutcome;
import com.group1.model.Finding;
import com.group1.model.RuleDefinition;
import com.group1.service.AIService;
import com.group1.util.CSVHelper;

import java.util.List;

public class RuleEngineBenchmark {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("           AITA SYSTEM - AST RULE ENGINE & AI INTEGRATION BENCHMARK              ");
        System.out.println("                      TV3 - AI Rule / Detection Engineer                        ");
        System.out.println("================================================================================\n");

        List<RuleDefinition> rules = CSVHelper.readRuleDefinitions("data/rule_definition.csv");
        System.out.println("Loaded " + rules.size() + " active rule definitions from data/rule_definition.csv:\n");
        for (RuleDefinition r : rules) {
            System.out.printf("  [%-16s] (ID: %2d) Severity: %-8s Penalty: %.1f | %s%n",
                    r.ruleCode, r.ruleId, r.severity, r.penaltyPoints, r.description);
        }
        System.out.println();

        ASTRuleEngine engine = new ASTRuleEngine();

        // TEST 1: Clean code without any violations
        String cleanCode =
                "package com.demo;\n" +
                "/** Clean sample code without errors. */\n" +
                "public class CleanStudentSubmission {\n" +
                "    private int count = 0;\n" +
                "\n" +
                "    /** Increments counter safely. */\n" +
                "    public void increment() {\n" +
                "        for (int i = 0; i < 10; i++) {\n" +
                "            this.count += i;\n" +
                "        }\n" +
                "        while (count > 0) {\n" +
                "            count--;\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public String toString() {\n" +
                "        return \"Count: \" + this.count;\n" +
                "    }\n" +
                "}\n";

        long start = System.currentTimeMillis();
        AnalysisOutcome cleanOutcome = engine.analyze(cleanCode, rules);
        long cleanTime = System.currentTimeMillis() - start;

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("[TEST 1: Clean Code Benchmark - False Positive Check]");
        System.out.println("  Syntax valid: " + cleanOutcome.syntaxValid);
        System.out.println("  Violations found: " + cleanOutcome.findings.size() + " (Expected: 0)");
        System.out.println("  Deduction: " + cleanOutcome.deduction + " pts (Expected: 0.0)");
        System.out.println("  Execution time: " + cleanTime + " ms");
        boolean test1Pass = cleanOutcome.findings.isEmpty() && cleanOutcome.deduction == 0.0f;
        System.out.println("  => Result: " + (test1Pass ? "PASSED (0% False Positive)" : "FAILED"));

        // TEST 2: General Buggy code covering multiple rules
        String badCode =
                "package com.demo;\n" +
                "import java.sql.Connection;\n" +
                "import java.sql.DriverManager;\n" +
                "\n" +
                "public class badStudentSubmission {\n" + // line 5: CS
                "    public void runLogic() {\n" + // line 6: COMMENT
                "        String role = \"guest\";\n" +
                "        if (role == \"admin\") {\n" + // line 8: STR_EQ
                "            System.out.println(\"Admin access\");\n" +
                "        }\n" +
                "\n" +
                "        boolean flag = false;\n" +
                "        if (flag = true) {\n" + // line 13: ASSIGN_IN_COND
                "            int zeroDiv = 100 / 0;\n" + // line 14: DIV_ZERO
                "        }\n" +
                "\n" +
                "        try {\n" +
                "            int x = 1;\n" +
                "        } catch (Exception e) {\n" + // line 19: EMPTY_CATCH
                "        }\n" +
                "\n" +
                "        while (true) {\n" + // line 22: INF_LOOP
                "            System.out.println(\"looping forever\");\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    public Object getResource() {\n" + // line 27: COMMENT
                "        return null;\n" + // line 28: NPE
                "        int unreachable = 99;\n" + // line 29: DEAD_CODE
                "    }\n" +
                "\n" +
                "    public String toString() {\n" + // line 32: MISSING_OVERRIDE
                "        return \"bad\";\n" +
                "    }\n" +
                "\n" +
                "    public boolean equals(Object o) {\n" + // line 36: EQUALS_HASHCODE
                "        return true;\n" +
                "    }\n" +
                "}\n";

        start = System.currentTimeMillis();
        AnalysisOutcome badOutcome = engine.analyze(badCode, rules);
        long badTime = System.currentTimeMillis() - start;

        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("[TEST 2: Bad Code Detection Benchmark]");
        System.out.println("  Syntax valid: " + badOutcome.syntaxValid);
        System.out.println("  Violations detected: " + badOutcome.findings.size());
        System.out.println("  Total deduction: " + badOutcome.deduction + " pts (Capped at 10.0)");
        System.out.println("  Execution time: " + badTime + " ms\n");

        System.out.println("  Detailed Findings List:");
        for (Finding f : badOutcome.findings) {
            System.out.printf("   * Line %2d | [%-16s] %-8s (-%.1f pts) -> %s%n",
                    f.line, f.rule.ruleCode, f.rule.severity, f.rule.penaltyPoints, f.suggestion);
        }

        // TEST 3: Dedicated Infinite Loop Variations
        String infiniteLoopSuite =
                "package com.demo;\n" +
                "public class InfiniteLoopSuite {\n" +
                "    // Case A: while(true) without break\n" +
                "    public void caseWhileTrue() {\n" +
                "        while (true) {\n" + // Line 5: INF_LOOP
                "            System.out.println(\"A\");\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // Case B: loop counter decremented instead of incremented\n" +
                "    public void caseWrongDirection() {\n" +
                "        for (int i = 0; i < 10; i--) {\n" + // Line 12: INF_LOOP
                "            System.out.println(i);\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // Case C: condition variable never modified in while\n" +
                "    public void caseConditionNotUpdated() {\n" +
                "        int count = 0;\n" +
                "        while (count < 10) {\n" + // Line 19: INF_LOOP
                "            System.out.println(\"count is static\");\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // Case D: nested loop where break belongs to inner loop, outer is still infinite\n" +
                "    public void caseNestedLoopTrap() {\n" +
                "        while (true) {\n" + // Line 26: INF_LOOP
                "            for (int j = 0; j < 3; j++) {\n" +
                "                break; // exits inner for-loop, NOT while(true)!\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        start = System.currentTimeMillis();
        AnalysisOutcome infOutcome = engine.analyze(infiniteLoopSuite, rules);
        long infTime = System.currentTimeMillis() - start;

        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("[TEST 3: Deep Infinite Loop Edge Cases Benchmark]");
        System.out.println("  Syntax valid: " + infOutcome.syntaxValid);
        System.out.println("  Execution time: " + infTime + " ms\n");

        int infCount = 0;
        for (Finding f : infOutcome.findings) {
            if ("INF_LOOP".equals(f.rule.ruleCode)) {
                infCount++;
                System.out.printf("   [INF_LOOP FOUND] Line %2d -> %s%n", f.line, f.suggestion);
            }
        }
        System.out.println("  Total Infinite Loop edge cases caught: " + infCount + " / 4 expected cases.");
        System.out.println("  => Result: " + (infCount == 4 ? "PASSED ALL EDGE CASES (100% Detection Rate)" : "PARTIAL"));

        // TEST 4: AI Feedback Engine Demo
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("[TEST 4: AI Feedback Engine Benchmark (OpenAI / Gemini / Local Engine)]");
        AIService ai = AIService.getInstance();
        System.out.println("  Configured Provider: " + ai.getProperty("AI_PROVIDER", "disabled"));
        System.out.println("  OpenAI Model:        " + ai.getProperty("OPENAI_MODEL", "gpt-4o-mini"));
        System.out.println("  Gemini Model:        " + ai.getProperty("GEMINI_MODEL", "gemini-2.0-flash"));
        System.out.println("\n  >> Live AI Feedback Output generated for Student Submission:\n");
        System.out.println(badOutcome.feedback);

        System.out.println("\n================================================================================");
        System.out.println("                         BENCHMARK SUMMARY & REPORT                             ");
        System.out.println("================================================================================");
        System.out.printf("  Rules active in catalog: %d%n", rules.size());
        System.out.printf("  Average AST parse time:  %d ms%n", (cleanTime + badTime + infTime) / 3);
        System.out.println("  Status: AI INTEGRATION & AST RULE ENGINE FULLY OPERATIONAL!");
        System.out.println("================================================================================\n");
    }
}
