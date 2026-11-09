package com.group1.rule;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.group1.model.AnalysisOutcome;
import com.group1.model.Finding;
import com.group1.model.RuleDefinition;
import com.group1.rule.visitor.*;
import com.group1.service.AIService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ASTRuleEngine {

    private final List<VoidVisitorAdapter<List<RuleViolationResult>>> visitors = new ArrayList<>();

    public ASTRuleEngine() {
        // Register all AST rule visitors
        // 1. Core Logic & Infinite Loop
        visitors.add(new InfiniteLoopVisitor());
        visitors.add(new StringComparisonVisitor());
        visitors.add(new EmptyCatchVisitor());
        visitors.add(new ResourceLeakVisitor());

        // 2. 5 Additional Advanced Rules
        visitors.add(new DivisionByZeroVisitor());
        visitors.add(new AssignmentInConditionVisitor());
        visitors.add(new DeadCodeVisitor());
        visitors.add(new MissingOverrideVisitor());
        visitors.add(new EqualsHashCodeVisitor());

        // 3. Existing base rules
        visitors.add(new NullReturnVisitor());
        visitors.add(new MemoryLeakVisitor());
        visitors.add(new NamingConventionVisitor());
        visitors.add(new LongMethodVisitor());
        visitors.add(new MissingCommentVisitor());
        visitors.add(new DuplicateCodeVisitor());
    }

    public static List<RuleDefinition> getDefaultRules() {
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(new RuleDefinition(1, "INF_LOOP", "Infinite Loop", "Error", 10.0f, "Loop has no termination condition or counter runs backwards"));
        rules.add(new RuleDefinition(2, "STR_EQ", "String comparison with ==", "Warning", 2.0f, "Compare strings with .equals() instead of =="));
        rules.add(new RuleDefinition(3, "DIV_ZERO", "Division by zero", "Error", 3.0f, "Division by constant zero causes ArithmeticException"));
        rules.add(new RuleDefinition(4, "ASSIGN_IN_COND", "Assignment in condition", "Error", 2.5f, "Assignment operator = used instead of comparison =="));
        rules.add(new RuleDefinition(5, "DEAD_CODE", "Dead code after return", "Warning", 1.0f, "Unreachable code following return or throw statement"));
        rules.add(new RuleDefinition(6, "MISSING_OVERRIDE", "Missing @Override", "Info", 1.0f, "Subclass method overrides superclass/interface without @Override"));
        rules.add(new RuleDefinition(7, "EQUALS_HASHCODE", "equals without hashCode", "Warning", 1.5f, "Overriding equals() requires overriding hashCode()"));
        rules.add(new RuleDefinition(8, "DUPLICATE_CODE", "Duplicate code block", "Warning", 1.5f, "Identical method body found in same class"));
        rules.add(new RuleDefinition(9, "RES_LEAK", "Resource leak", "Error", 2.5f, "Connection, Statement, or ResultSet not closed"));
        rules.add(new RuleDefinition(10, "EMPTY_CATCH", "Empty catch block", "Warning", 1.5f, "Catch block catches exception but does nothing"));
        rules.add(new RuleDefinition(11, "NPE", "Null return", "Warning", 2.0f, "Avoid returning null directly without checks"));
        rules.add(new RuleDefinition(12, "OOM", "Memory leak in loop", "Critical", 5.0f, "Allocating large objects or arrays inside loops"));
        rules.add(new RuleDefinition(13, "CS", "Naming convention", "Low", 0.5f, "Class names must start with uppercase letter"));
        rules.add(new RuleDefinition(14, "LONG_METHOD", "Long method", "Medium", 1.0f, "Method exceeds 40 lines of code"));
        rules.add(new RuleDefinition(15, "COMMENT", "Missing comments", "Low", 0.5f, "Public classes and methods should have Javadoc comments"));
        return rules;
    }

    public AnalysisOutcome analyze(String sourceCode) {
        return analyze(sourceCode, getDefaultRules());
    }

    public AnalysisOutcome analyze(String sourceCode, List<RuleDefinition> configuredRules) {
        AnalysisOutcome outcome = new AnalysisOutcome();
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(sourceCode);
        } catch (ParseProblemException exception) {
            outcome.syntaxValid = false;
            String message = exception.getMessage();
            outcome.feedback = "Java syntax could not be parsed. "
                    + (message == null ? "Check the submitted source." : message.split("\\n", 2)[0]);
            return outcome;
        }

        outcome.syntaxValid = true;
        outcome.classCount = unit.findAll(ClassOrInterfaceDeclaration.class).size();
        outcome.methodCount = unit.findAll(MethodDeclaration.class).size();

        // Run visitors to collect violations
        List<RuleViolationResult> rawViolations = new ArrayList<>();
        for (VoidVisitorAdapter<List<RuleViolationResult>> visitor : visitors) {
            visitor.visit(unit, rawViolations);
        }

        // Map ruleCode -> RuleDefinition for quick lookup
        Map<String, RuleDefinition> ruleMap = new HashMap<>();
        if (configuredRules != null) {
            for (RuleDefinition rule : configuredRules) {
                if (rule.ruleCode != null) {
                    ruleMap.put(rule.ruleCode.trim().toUpperCase(), rule);
                }
            }
        }

        // De-duplicate findings by (ruleCode + ":" + lineNumber)
        Set<String> seen = new HashSet<>();
        for (RuleViolationResult v : rawViolations) {
            String codeKey = v.ruleCode.toUpperCase();
            RuleDefinition rule = ruleMap.get(codeKey);
            if (rule == null) {
                // Rule not found in DB or disabled, skip
                continue;
            }

            String key = rule.ruleId + ":" + v.lineNumber;
            if (seen.add(key)) {
                outcome.findings.add(new Finding(rule, v.lineNumber, v.suggestedFix));
                outcome.deduction += rule.penaltyPoints;
            }
        }

        outcome.deduction = Math.min(10.0f, outcome.deduction);

        if (outcome.findings.isEmpty()) {
            outcome.feedback = "AST analysis completed successfully. No configured rule violations detected.";
        } else {
            // Generate intelligent pedagogical feedback via OpenAI, Gemini, or Local Rule Explanation Engine
            outcome.feedback = AIService.getInstance().generateFeedback(sourceCode, outcome.findings);
        }

        return outcome;
    }
}
