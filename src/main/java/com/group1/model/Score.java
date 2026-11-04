package com.group1.model;

import java.io.Serializable;

public class Score implements Serializable {
    private static final long serialVersionUID = 1L;

    public int scoreId;
    public String submissionId;
    public float totalScore;
    public float deduction;
    public String gradingNotes;
    public String updatedAt;

    public Score() {
    }

    public Score(int scoreId, String submissionId, float totalScore, float deduction, String gradingNotes,
            String updatedAt) {
        this.scoreId = scoreId;
        this.submissionId = submissionId;
        this.totalScore = totalScore;
        this.deduction = deduction;
        this.gradingNotes = gradingNotes;
        this.updatedAt = updatedAt;
    }

    public int getScoreId() {
        return scoreId;
    }

    public void setScoreId(int scoreId) {
        this.scoreId = scoreId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public float getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(float totalScore) {
        this.totalScore = totalScore;
    }

    public float getDeduction() {
        return deduction;
    }

    public void setDeduction(float deduction) {
        this.deduction = deduction;
    }

    public String getGradingNotes() {
        return gradingNotes;
    }

    public void setGradingNotes(String gradingNotes) {
        this.gradingNotes = gradingNotes;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Score{" +
                "scoreId=" + scoreId +
                ", submissionId='" + submissionId + '\'' +
                ", totalScore=" + totalScore +
                ", deduction=" + deduction +
                '}';
    }
}
