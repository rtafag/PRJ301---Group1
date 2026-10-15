package com.group1.model;

import java.io.Serializable;

public class RuleViolation implements Serializable {
    private static final long serialVersionUID = 1L;

    public int violationId;
    public int analysisId;
    public int ruleId;
    public String fileName;
    public int lineNumber;
    public String suggestedFix;

    public RuleViolation() {
    }

    public RuleViolation(int violationId, int analysisId, int ruleId, String fileName, int lineNumber, String suggestedFix) {
        this.violationId = violationId;
        this.analysisId = analysisId;
        this.ruleId = ruleId;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.suggestedFix = suggestedFix;
    }

    public int getViolationId() {
        return violationId;
    }

    public void setViolationId(int violationId) {
        this.violationId = violationId;
    }

    public int getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(int analysisId) {
        this.analysisId = analysisId;
    }

    public int getRuleId() {
        return ruleId;
    }

    public void setRuleId(int ruleId) {
        this.ruleId = ruleId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public void setSuggestedFix(String suggestedFix) {
        this.suggestedFix = suggestedFix;
    }

    @Override
    public String toString() {
        return "RuleViolation{" +
                "violationId=" + violationId +
                ", analysisId=" + analysisId +
                ", ruleId=" + ruleId +
                ", fileName='" + fileName + '\'' +
                ", lineNumber=" + lineNumber +
                '}';
    }
}
