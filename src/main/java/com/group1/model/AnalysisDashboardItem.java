package com.group1.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisDashboardItem {
    private final int analysisId;
    private final String submissionId;
    private final String aiFeedback;
    private final int executionTimeMs;
    private final List<Finding> findings = new ArrayList<>();

    public AnalysisDashboardItem(int analysisId, String submissionId, String aiFeedback, int executionTimeMs) {
        this.analysisId = analysisId;
        this.submissionId = submissionId;
        this.aiFeedback = aiFeedback;
        this.executionTimeMs = executionTimeMs;
    }

    public int getAnalysisId() {
        return analysisId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public int getExecutionTimeMs() {
        return executionTimeMs;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void addFinding(Finding finding) {
        findings.add(finding);
    }

    public static class Finding {
        private final String ruleCode;
        private final String ruleName;
        private final String severity;
        private final float penaltyPoints;
        private final String description;
        private final String fileName;
        private final int lineNumber;
        private final String suggestedFix;

        public Finding(String ruleCode, String ruleName, String severity, float penaltyPoints,
                String description, String fileName, int lineNumber, String suggestedFix) {
            this.ruleCode = ruleCode;
            this.ruleName = ruleName;
            this.severity = severity;
            this.penaltyPoints = penaltyPoints;
            this.description = description;
            this.fileName = fileName;
            this.lineNumber = lineNumber;
            this.suggestedFix = suggestedFix;
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

        public float getPenaltyPoints() {
            return penaltyPoints;
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
}
