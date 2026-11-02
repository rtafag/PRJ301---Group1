package com.group1.orm;

import com.group1.model.AnalysisResult;
import com.group1.model.RuleViolation;
import com.group1.model.Score;
import com.group1.model.Submission;
import java.util.ArrayList;
import java.util.List;

/**
 * Runnable demo demonstrating the JPA/ORM Grading Job synchronization across
 * Submission, AnalysisResult, and Score tables.
 */
public class GradingJobDemo {
    public static void main(String[] args) {
        System.out.println("=== DEMO: JPA/ORM GRADING JOB TRANSACTIONAL SYNCHRONIZATION ===");
        GradingJobORMService service = new GradingJobORMService();

        try {
            // 1. Fetch an existing submission (e.g. SUB0001)
            String targetSubmissionId = "SUB0001";
            GradingJobORMService.GradingJobAggregate initial = service.getGradingJobAggregate(targetSubmissionId);
            System.out.println("Initial State from Database:");
            System.out.println("-> " + initial);

            // 2. Prepare atomic update objects
            Submission sub = initial.submission;
            Score newScore = new Score(
                    initial.score != null ? initial.score.getScoreId() : 9999,
                    targetSubmissionId,
                    9.8f,
                    0.2f,
                    "Automated AST grading: 1 minor naming issue resolved.",
                    "2026-11-02 12:00:00"
            );
            AnalysisResult newAnal = new AnalysisResult(
                    initial.analysisResult != null ? initial.analysisResult.getAnalysisId() : 9999,
                    targetSubmissionId,
                    "{\"status\":\"AST_OPTIMIZED\",\"nodesCount\":42}",
                    "AI Feedback: Clean code structure, high maintainability.",
                    85
            );
            List<RuleViolation> vios = new ArrayList<>();

            // 3. Atomically process the grading job
            System.out.println("\nProcessing Grading Job via Transactional Unit of Work...");
            boolean success = service.processGradingJob(sub, newScore, newAnal, vios);
            System.out.println("Grading Job Processed: " + success);

            // 4. Verify updated synchronized state
            GradingJobORMService.GradingJobAggregate updated = service.getGradingJobAggregate(targetSubmissionId);
            System.out.println("\nUpdated Synchronized State in Database:");
            System.out.println("-> " + updated);
            System.out.println("-> Score Notes: " + updated.score.getGradingNotes());
            System.out.println("-> AI Feedback: " + updated.analysisResult.getAiFeedback());
            System.out.println("=== JPA/ORM GRADING JOB SYNCHRONIZATION COMPLETED SUCCESSFULLY! ===");
        } catch (Exception e) {
            System.err.println("Demo Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
