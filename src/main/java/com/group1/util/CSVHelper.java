package com.group1.util;

import com.group1.model.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CSVHelper {
    public static List<Users> readUsers(String path) {
        List<Users> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", -1);
                if(d.length>=7) res.add(new Users(d[0], d[1], d[2], d[3], d[4], d[5], d[6]));
            }
        } catch(Exception e){} return res;
    }
    public static List<RuleDefinition> readRuleDefinitions(String path) {
        List<RuleDefinition> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", -1);
                if(d.length>=6) res.add(new RuleDefinition(Integer.parseInt(d[0]), d[1], d[2], d[3], Float.parseFloat(d[4]), d[5]));
            }
        } catch(Exception e){} return res;
    }
    public static List<Submission> readSubmissions(String path) {
        List<Submission> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", -1);
                if(d.length>=6) res.add(new Submission(d[0], d[1], d[2], d[3], d[4], d[5]));
            }
        } catch(Exception e){} return res;
    }
    public static List<Score> readScores(String path) {
        List<Score> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", -1);
                if(d.length>=6) res.add(new Score(Integer.parseInt(d[0]), d[1], Float.parseFloat(d[2]), Float.parseFloat(d[3]), d[4], d[5]));
            }
        } catch(Exception e){} return res;
    }
    public static List<AnalysisResult> readAnalysisResults(String path) {
        List<AnalysisResult> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", -1);
                if(d.length>=5) res.add(new AnalysisResult(Integer.parseInt(d[0]), d[1], d[2], d[3], Integer.parseInt(d[4])));
            }
        } catch(Exception e){} return res;
    }
    public static List<RuleViolation> readRuleViolations(String path) {
        List<RuleViolation> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", -1);
                if(d.length>=6) res.add(new RuleViolation(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]), d[3], Integer.parseInt(d[4]), d[5]));
            }
        } catch(Exception e){} return res;
    }
}
