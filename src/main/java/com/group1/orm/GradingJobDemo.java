package com.group1.orm;

import com.group1.model.AnalysisResult;
import com.group1.model.RuleViolation;
import com.group1.model.Score;
import com.group1.model.Submission;
import com.group1.util.JpaUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise runnable demo demonstrating JPA / Hibernate ORM Grading Job synchronization
 * across Submission, AnalysisResult, Score, and RuleViolation tables.
 */
public class GradingJobDemo {
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("=== DEMO: ENTERPRISE JPA / HIBERNATE ORM GRADING JOB SYNCHRONIZATION ===");
        System.out.println("================================================================================");
        GradingJobORMService service = new GradingJobORMService();

        try {
            // 1. Fetch an existing submission (e.g. SUB0001)
            String targetSubmissionId = "SUB0001";
            GradingJobORMService.GradingJobAggregate initial = service.getGradingJobAggregate(targetSubmissionId);
            System.out.println("\n[1] Initial State from Database:");
            System.out.println("-> " + initial);

            // 2. Prepare atomic update objects
            Submission sub = initial.submission;
            Score newScore = new Score(
                    initial.score != null ? initial.score.getScoreId() : 9999,
                    targetSubmissionId,
                    9.8f,
                    0.2f,
                    "Automated AST grading: 1 minor naming issue resolved.",
                    "2026-11-09 10:00:00"
            );
            AnalysisResult newAnal = new AnalysisResult(
                    initial.analysisResult != null ? initial.analysisResult.getAnalysisId() : 9999,
                    targetSubmissionId,
                    "{\"status\":\"AST_OPTIMIZED\",\"nodesCount\":42}",
                    "AI Feedback: Clean code structure, high maintainability.",
                    85
            );
            List<RuleViolation> vios = new ArrayList<>();

            // 3. Atomically process the grading job via JPA / Hibernate EntityManager
            System.out.println("\n[2] Executing JPA Transaction via EntityManager & Hibernate ORM...");
            boolean jpaSuccess = service.processGradingJobViaJPA(sub, newScore, newAnal, vios);
            System.out.println("-> JPA / Hibernate Transaction Result: " + (jpaSuccess ? "SUCCESS (COMMITTED)" : "FAILED"));

            // 4. Verify updated synchronized state
            GradingJobORMService.GradingJobAggregate updated = service.getGradingJobAggregate(targetSubmissionId);
            System.out.println("\n[3] Updated Synchronized State in Database:");
            System.out.println("-> " + updated);
            System.out.println("-> Score Notes: " + (updated.score != null ? updated.score.getGradingNotes() : "N/A"));
            System.out.println("-> AI Feedback: " + (updated.analysisResult != null ? updated.analysisResult.getAiFeedback() : "N/A"));
            System.out.println("\n================================================================================");
            System.out.println("=== JPA / HIBERNATE ORM GRADING JOB SYNCHRONIZATION COMPLETED SUCCESSFULLY! ===");
            System.out.println("================================================================================");
        } catch (Exception e) {
            System.err.println("Demo Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            JpaUtil.shutdown();
        }
    }
}
