package com.group1.model;

import java.io.Serializable;

public class AnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public int analysisId;
    public String submissionId;
    public String astJson;
    public String aiFeedback;
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
