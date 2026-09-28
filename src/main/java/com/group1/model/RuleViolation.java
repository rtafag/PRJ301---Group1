package com.group1.model;
public class RuleViolation {
    public int violationId; public int analysisId; public int ruleId;
    public String fileName; public int lineNumber; public String suggestedFix;
    public RuleViolation(int violationId, int analysisId, int ruleId, String fileName, int lineNumber, String suggestedFix) {
        this.violationId=violationId; this.analysisId=analysisId; this.ruleId=ruleId;
        this.fileName=fileName; this.lineNumber=lineNumber; this.suggestedFix=suggestedFix;
    }
}
