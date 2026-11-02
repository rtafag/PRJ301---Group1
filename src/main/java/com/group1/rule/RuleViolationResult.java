package com.group1.rule;

public class RuleViolationResult {
    public final String ruleCode;
    public final int lineNumber;
    public final String message;
    public final String suggestedFix;

    public RuleViolationResult(String ruleCode, int lineNumber, String message, String suggestedFix) {
        this.ruleCode = ruleCode;
        this.lineNumber = lineNumber;
        this.message = message;
        this.suggestedFix = suggestedFix;
    }

    @Override
    public String toString() {
        return "[" + ruleCode + "] Line " + lineNumber + ": " + message;
    }
}
