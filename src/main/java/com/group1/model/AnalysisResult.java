package com.group1.model;
public class AnalysisResult {
    public int analysisId; public String submissionId; public String astJson;
    public String aiFeedback; public int executionTimeMs;
    public AnalysisResult(int analysisId, String submissionId, String astJson, String aiFeedback, int executionTimeMs) {
        this.analysisId=analysisId; this.submissionId=submissionId; this.astJson=astJson;
        this.aiFeedback=aiFeedback; this.executionTimeMs=executionTimeMs;
    }
}
