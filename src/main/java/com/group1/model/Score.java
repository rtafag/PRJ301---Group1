package com.group1.model;
public class Score {
    public int scoreId; public String submissionId; public float totalScore;
    public float deduction; public String gradingNotes; public String updatedAt;
    public Score(int scoreId, String submissionId, float totalScore, float deduction, String gradingNotes, String updatedAt) {
        this.scoreId=scoreId; this.submissionId=submissionId; this.totalScore=totalScore;
        this.deduction=deduction; this.gradingNotes=gradingNotes; this.updatedAt=updatedAt;
    }
}
