package com.group1.dao;

import com.group1.model.Submission;
import java.util.List;

public interface SubmissionDAO {
    void insert(Submission submission);
    Submission getById(String submissionId);
    List<Submission> getAll();
    void updateScore(String submissionId, double newScore, String scorePublicTime);
    void truncateTable(); 
}
