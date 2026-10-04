package com.group1.console;

import com.group1.model.AnalysisResult;
import com.group1.model.RuleDefinition;
import com.group1.model.RuleViolation;
import com.group1.model.Score;
import com.group1.model.Submission;
import com.group1.model.Users;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConsoleDataStore {
    private final Path dataDirectory;

    public ConsoleDataStore() {
        this(Paths.get(System.getProperty("aita.dataDir", "data")));
    }

    public ConsoleDataStore(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Path getFile(String fileName) {
        return dataDirectory.resolve(fileName);
    }

    public List<Users> readUsers() {
        List<Users> users = new ArrayList<>();
        for (String[] row : readRecords("users.csv")) {
            if (row.length >= 7) {
                users.add(new Users(row[0], row[1], row[2], row[3], row[4], row[5], row[6]));
            }
        }
        return users;
    }

    public List<Submission> readSubmissions() {
        List<Submission> submissions = new ArrayList<>();
        for (String[] row : readRecords("submission.csv")) {
            if (row.length >= 6) {
                submissions.add(new Submission(row[0], row[1], row[2], row[3], row[4], row[5]));
            }
        }
        return submissions;
    }

    public List<Score> readScores() {
        List<Score> scores = new ArrayList<>();
        for (String[] row : readRecords("score.csv")) {
            if (row.length >= 6) {
                try {
                    scores.add(new Score(Integer.parseInt(row[0]), row[1], Float.parseFloat(row[2]),
                            Float.parseFloat(row[3]), row[4], row[5]));
                } catch (NumberFormatException exception) {
                    System.err.println("Skipping invalid score row in score.csv: " + row[0]);
                }
            }
        }
        return scores;
    }

    public List<AnalysisResult> readAnalysisResults() {
        List<AnalysisResult> results = new ArrayList<>();
        for (String[] row : readRecords("analysis_result.csv")) {
            if (row.length >= 5) {
                try {
                    results.add(new AnalysisResult(Integer.parseInt(row[0]), row[1], row[2], row[3],
                            Integer.parseInt(row[4])));
                } catch (NumberFormatException exception) {
                    System.err.println("Skipping invalid analysis row in analysis_result.csv: " + row[0]);
                }
            }
        }
        return results;
    }

    public List<RuleDefinition> readRules() {
        List<RuleDefinition> rules = new ArrayList<>();
        for (String[] row : readRecords("rule_definition.csv")) {
            if (row.length >= 6) {
                try {
                    rules.add(new RuleDefinition(Integer.parseInt(row[0]), row[1], row[2], row[3],
                            Float.parseFloat(row[4]), row[5]));
                } catch (NumberFormatException exception) {
                    System.err.println("Skipping invalid rule row in rule_definition.csv: " + row[0]);
                }
            }
        }
        return rules;
    }

    public List<RuleViolation> readViolations() {
        List<RuleViolation> violations = new ArrayList<>();
        for (String[] row : readRecords("rule_violation.csv")) {
            if (row.length >= 6) {
                try {
                    violations.add(new RuleViolation(Integer.parseInt(row[0]), Integer.parseInt(row[1]),
                            Integer.parseInt(row[2]), row[3], Integer.parseInt(row[4]), row[5]));
                } catch (NumberFormatException exception) {
                    System.err.println("Skipping invalid violation row in rule_violation.csv: " + row[0]);
                }
            }
        }
        return violations;
    }

    public List<String[]> readManualFeedback() {
        return readRecords("manual_feedback.csv");
    }

    public void saveSubmissions(List<Submission> submissions) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Submission submission : submissions) {
            rows.add(new String[] { submission.submissionId, submission.userId, submission.fileUrl,
                    submission.fileHash, submission.status, submission.submittedAt });
        }
        writeRecords("submission.csv", "submissionId,userId,fileUrl,fileHash,status,submittedAt", rows);
    }

    public void saveScores(List<Score> scores) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (Score score : scores) {
            rows.add(new String[] { String.valueOf(score.scoreId), score.submissionId,
                    String.valueOf(score.totalScore), String.valueOf(score.deduction),
                    score.gradingNotes, score.updatedAt });
        }
        writeRecords("score.csv", "scoreId,submissionId,totalScore,deduction,gradingNotes,updatedAt", rows);
    }

    public void saveAnalysisResults(List<AnalysisResult> results) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (AnalysisResult result : results) {
            rows.add(new String[] { String.valueOf(result.analysisId), result.submissionId,
                    result.astJson, result.aiFeedback, String.valueOf(result.executionTimeMs) });
        }
        writeRecords("analysis_result.csv", "analysisId,submissionId,astJson,aiFeedback,executionTimeMs", rows);
    }

    public void saveViolations(List<RuleViolation> violations) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (RuleViolation violation : violations) {
            rows.add(new String[] { String.valueOf(violation.violationId), String.valueOf(violation.analysisId),
                    String.valueOf(violation.ruleId), violation.fileName, String.valueOf(violation.lineNumber),
                    violation.suggestedFix });
        }
        writeRecords("rule_violation.csv", "violationId,analysisId,ruleId,fileName,lineNumber,suggestedFix", rows);
    }

    public void saveRules(List<RuleDefinition> rules) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (RuleDefinition rule : rules) {
            rows.add(new String[] { String.valueOf(rule.ruleId), rule.ruleCode, rule.ruleName, rule.severity,
                    String.valueOf(rule.penaltyPoints), rule.description });
        }
        writeRecords("rule_definition.csv", "ruleId,ruleCode,ruleName,severity,penaltyPoints,description", rows);
    }

    public void saveManualFeedback(List<String[]> feedback) throws IOException {
        writeRecords("manual_feedback.csv", "feedbackId,submissionId,teacherId,feedback,createdAt", feedback);
    }

    public String nextSubmissionId() {
        int maximum = 0;
        for (Submission submission : readSubmissions()) {
            String digits = submission.submissionId.replaceAll("[^0-9]", "");
            if (!digits.isEmpty())
                maximum = Math.max(maximum, Integer.parseInt(digits));
        }
        return String.format("SUB%04d", maximum + 1);
    }

    public int nextScoreId() {
        int maximum = 0;
        for (Score score : readScores())
            maximum = Math.max(maximum, score.scoreId);
        return maximum + 1;
    }

    public int nextAnalysisId() {
        int maximum = 0;
        for (AnalysisResult result : readAnalysisResults())
            maximum = Math.max(maximum, result.analysisId);
        return maximum + 1;
    }

    public int nextViolationId() {
        int maximum = 0;
        for (RuleViolation violation : readViolations())
            maximum = Math.max(maximum, violation.violationId);
        return maximum + 1;
    }

    private List<String[]> readRecords(String fileName) {
        List<String[]> records = new ArrayList<>();
        Path file = getFile(fileName);
        if (!Files.exists(file))
            return records;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StringBuilder record = new StringBuilder();
            String line;
            boolean insideQuotes = false;
            boolean firstRecord = true;
            while ((line = reader.readLine()) != null) {
                if (record.length() > 0)
                    record.append('\n');
                record.append(line);
                insideQuotes = updateQuoteState(line, insideQuotes);
                if (insideQuotes)
                    continue;

                if (firstRecord) {
                    firstRecord = false;
                } else {
                    records.add(parseRecord(record.toString()));
                }
                record.setLength(0);
            }
            if (record.length() > 0 && !firstRecord)
                records.add(parseRecord(record.toString()));
        } catch (IOException exception) {
            System.err.println("Unable to read " + file + ": " + exception.getMessage());
        }
        return records;
    }

    private boolean updateQuoteState(String line, boolean insideQuotes) {
        for (int index = 0; index < line.length(); index++) {
            if (line.charAt(index) != '"')
                continue;
            if (insideQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                index++;
            } else {
                insideQuotes = !insideQuotes;
            }
        }
        return insideQuotes;
    }

    private String[] parseRecord(String record) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        for (int index = 0; index < record.length(); index++) {
            char character = record.charAt(index);
            if (character == '"') {
                if (insideQuotes && index + 1 < record.length() && record.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (character == ',' && !insideQuotes) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        fields.add(field.toString());
        if (!fields.isEmpty() && fields.get(0).startsWith("\uFEFF")) {
            fields.set(0, fields.get(0).substring(1));
        }
        return fields.toArray(new String[0]);
    }

    private void writeRecords(String fileName, String header, List<String[]> records) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = getFile(fileName);
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(header);
            writer.newLine();
            for (String[] record : records) {
                for (int index = 0; index < record.length; index++) {
                    if (index > 0)
                        writer.write(',');
                    writer.write(escape(record[index]));
                }
                writer.newLine();
            }
        }
    }

    private String escape(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}