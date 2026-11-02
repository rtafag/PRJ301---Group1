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
