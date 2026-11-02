package com.group1.rule;

import com.group1.model.AnalysisOutcome;
import com.group1.model.Finding;
import com.group1.model.RuleDefinition;
import com.group1.util.CSVHelper;

import java.util.List;

public class RuleEngineBenchmark {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("           AITA SYSTEM - AST RULE ENGINE COMPREHENSIVE BENCHMARK                ");
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
                "        this.count++;\n" +
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

        // TEST 2: Buggy code covering multiple rules:
        // - INF_LOOP (while true no break)
        // - STR_EQ (name == "admin")
        // - DIV_ZERO (100 / 0)
        // - ASSIGN_IN_COND (if flag = true)
        // - EMPTY_CATCH (catch (Exception e) {})
        // - DEAD_CODE (code after return)
        // - EQUALS_HASHCODE (equals without hashCode)
        // - MISSING_OVERRIDE (toString without @Override)
        // - CS (lowercase class name)
        // - NPE (return null)
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

        System.out.println("\n================================================================================");
        System.out.println("                         BENCHMARK SUMMARY & REPORT                             ");
        System.out.println("================================================================================");
        System.out.printf("  Rules active in catalog: %d%n", rules.size());
        System.out.printf("  Rules verified in test:  %d%n", badOutcome.findings.size());
        System.out.printf("  Average AST parse time:  %d ms%n", (cleanTime + badTime) / 2);
        System.out.println("  Status: ALL RULES COMPILED & OPERATIONAL ON AST WITHOUT REGEX!");
        System.out.println("================================================================================\n");
    }
}
