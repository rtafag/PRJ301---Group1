package com.group1.model;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "RuleViolation")
public class RuleViolation implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "violation_id", nullable = false)
    public int violationId;

    @Column(name = "analysis_id", nullable = false)
    public int analysisId;

    @Column(name = "rule_id", nullable = false)
    public int ruleId;

    @Column(name = "file_name", length = 255)
    public String fileName;

    @Column(name = "line_number")
    public int lineNumber;

    @Column(name = "suggested_fix", columnDefinition = "NVARCHAR(MAX)")
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
