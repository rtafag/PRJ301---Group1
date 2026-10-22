package com.group1.util;

import com.group1.model.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CSVHelper {

    private static BufferedReader openReader(String path) throws Exception {
        return new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
    }

    public static List<Users> readUsers(String path) {
        List<Users> res = new ArrayList<>();
        try (BufferedReader br = openReader(path)) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] d = line.split(",", -1);
                if (d.length >= 7) {
                    res.add(new Users(d[0].trim(), d[1].trim(), d[2].trim(), d[3].trim(), d[4].trim(), d[5].trim(), d[6].trim()));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading users: " + e.getMessage());
        }
        return res;
    }

    public static List<RuleDefinition> readRuleDefinitions(String path) {
        List<RuleDefinition> res = new ArrayList<>();
        try (BufferedReader br = openReader(path)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] d = line.split(",", -1);
                if (d.length >= 6) {
                    res.add(new RuleDefinition(Integer.parseInt(d[0].trim()), d[1].trim(), d[2].trim(), d[3].trim(), Float.parseFloat(d[4].trim()), d[5].trim()));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading rules: " + e.getMessage());
        }
        return res;
    }

    public static List<Submission> readSubmissions(String path) {
        List<Submission> res = new ArrayList<>();
        try (BufferedReader br = openReader(path)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] d = line.split(",", -1);
                if (d.length >= 6) {
                    res.add(new Submission(d[0].trim(), d[1].trim(), d[2].trim(), d[3].trim(), d[4].trim(), d[5].trim()));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading submissions: " + e.getMessage());
        }
        return res;
    }

    public static List<Score> readScores(String path) {
        List<Score> res = new ArrayList<>();
        try (BufferedReader br = openReader(path)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] d = line.split(",", -1);
                if (d.length >= 6) {
                    res.add(new Score(Integer.parseInt(d[0].trim()), d[1].trim(), Float.parseFloat(d[2].trim()), Float.parseFloat(d[3].trim()), d[4].trim(), d[5].trim()));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading scores: " + e.getMessage());
        }
        return res;
    }

    public static List<AnalysisResult> readAnalysisResults(String path) {
        List<AnalysisResult> res = new ArrayList<>();
        try (BufferedReader br = openReader(path)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] d = line.split(",", -1);
                if (d.length >= 5) {
                    res.add(new AnalysisResult(Integer.parseInt(d[0].trim()), d[1].trim(), d[2].trim(), d[3].trim(), Integer.parseInt(d[4].trim())));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading analysis results: " + e.getMessage());
        }
        return res;
    }

    public static List<RuleViolation> readRuleViolations(String path) {
        List<RuleViolation> res = new ArrayList<>();
        try (BufferedReader br = openReader(path)) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] d = line.split(",", -1);
                if (d.length >= 6) {
                    res.add(new RuleViolation(Integer.parseInt(d[0].trim()), Integer.parseInt(d[1].trim()), Integer.parseInt(d[2].trim()), d[3].trim(), Integer.parseInt(d[4].trim()), d[5].trim()));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading rule violations: " + e.getMessage());
        }
        return res;
    }
}
