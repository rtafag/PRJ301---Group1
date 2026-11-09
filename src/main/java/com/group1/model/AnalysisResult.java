package com.group1.model;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "AnalysisResult")
public class AnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "analysis_id", nullable = false)
    public int analysisId;

    @Column(name = "submission_id", length = 50, nullable = false, unique = true)
    public String submissionId;

    @Column(name = "ast_json", columnDefinition = "NVARCHAR(MAX)")
    public String astJson;

    @Column(name = "ai_feedback", columnDefinition = "NVARCHAR(MAX)")
    public String aiFeedback;

    @Column(name = "execution_time_ms")
    public int executionTimeMs;

    public AnalysisResult() {
    }

    public AnalysisResult(int analysisId, String submissionId, String astJson, String aiFeedback, int executionTimeMs) {
        this.analysisId = analysisId;
        this.submissionId = submissionId;
        this.astJson = astJson;
        this.aiFeedback = aiFeedback;
        this.executionTimeMs = executionTimeMs;
    }

    public int getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(int analysisId) {
        this.analysisId = analysisId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getAstJson() {
        return astJson;
    }

    public void setAstJson(String astJson) {
        this.astJson = astJson;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public void setAiFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    public int getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(int executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "analysisId=" + analysisId +
                ", submissionId='" + submissionId + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}
