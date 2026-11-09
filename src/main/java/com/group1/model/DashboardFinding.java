package com.group1.model;

import java.io.Serializable;

public class DashboardFinding implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String ruleCode;
    private final String ruleName;
    private final String severity;
    private final String description;
    private final String fileName;
    private final int lineNumber;
    private final String suggestedFix;

    public DashboardFinding(RuleDefinition rule, RuleViolation violation) {
        this.ruleCode = rule == null ? null : rule.getRuleCode();
        this.ruleName = rule == null ? "Quy tắc #" + violation.getRuleId() : rule.getRuleName();
        this.severity = rule == null ? "Chưa xác định" : rule.getSeverity();
        this.description = rule == null ? null : rule.getDescription();
        this.fileName = violation.getFileName();
        this.lineNumber = violation.getLineNumber();
        this.suggestedFix = violation.getSuggestedFix();
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }
}
