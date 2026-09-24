package com.group1.model;

public class Submission {
    private String submissionId; // Primary key
    private String submitTime;
    private double score;
    private String scorePublicTime;

    public Submission() {}

    public Submission(String submissionId, String submitTime, double score, String scorePublicTime) {
        this.submissionId = submissionId;
        this.submitTime = submitTime;
        this.score = score;
        this.scorePublicTime = scorePublicTime;
    }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
    public String getSubmitTime() { return submitTime; }
    public void setSubmitTime(String submitTime) { this.submitTime = submitTime; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getScorePublicTime() { return scorePublicTime; }
    public void setScorePublicTime(String scorePublicTime) { this.scorePublicTime = scorePublicTime; }

    @Override
    public String toString() {
        return "Submission{submissionId='" + submissionId + "', submitTime='" + submitTime + 
               "', score=" + score + ", scorePublicTime='" + scorePublicTime + "'}";
    }
}
