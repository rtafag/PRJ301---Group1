package com.group1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DataGenerator {
    private static final String[] LAST_NAMES = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ"};
    private static final String[] MIDDLE_NAMES = {"Văn", "Thị", "Hữu", "Ngọc", "Đức", "Minh", "Quang", "Xuân", "Thu", "Thanh"};
    private static final String[] FIRST_NAMES = {"Anh", "Tuấn", "Dũng", "Hoa", "Lan", "Hương", "Huy", "Cường", "Trang", "Linh"};

    public static String removeAccents(String s) {
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                         .replaceAll("Đ", "D").replaceAll("đ", "d")
                         .toLowerCase().replaceAll("\\s+", "");
    }

    public static void main(String[] args) {
        Random rand = new Random();
        Set<String> usedNames = new HashSet<>();
        List<String> allNames = new ArrayList<>();
        while(allNames.size() < 100) {
            String name = LAST_NAMES[rand.nextInt(LAST_NAMES.length)] + " " + 
                          MIDDLE_NAMES[rand.nextInt(MIDDLE_NAMES.length)] + " " + 
                          FIRST_NAMES[rand.nextInt(FIRST_NAMES.length)];
            if(usedNames.add(name)) allNames.add(name);
        }
        
        // 1. Users
        List<String> userIds = new ArrayList<>();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/users.csv", StandardCharsets.UTF_8))) {
            bw.write("userId,username,passwordHash,fullName,email,role,createdAt\n");
            for(int i=0; i<100; i++) {
                String uId = "U" + String.format("%04d", i+1);
                userIds.add(uId);
                String fullName = allNames.get(i);
                String[] parts = fullName.split(" ");
                String uname = removeAccents(parts[parts.length-1] + parts[0]) + (10+rand.nextInt(90));
                String pwd = "hash_" + uname;
                String email = uname + "@fpt.edu.vn";
                String role = (i < 10) ? "Teacher" : "Student";
                bw.write(String.format("%s,%s,%s,%s,%s,%s,2023-10-01 10:00:00\n", uId, uname, pwd, fullName, email, role));
            }
        } catch (Exception e) {}

        // 2. RuleDefinition
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/rule_definition.csv", StandardCharsets.UTF_8))) {
            bw.write("ruleId,ruleCode,ruleName,severity,penaltyPoints,description\n");
            bw.write("1,NPE,NullPointerException,High,2.0,Avoid returning null without check\n");
            bw.write("2,OOM,OutOfMemoryError,Critical,5.0,Memory leak detected in loop\n");
            bw.write("3,CS,CodeStyle,Low,0.5,Variable naming convention violation\n");
        } catch (Exception e) {}

        // 3. Submission & Score & AnalysisResult & RuleViolation
        int violationId = 1;
        try (
            BufferedWriter bwSub = new BufferedWriter(new FileWriter("data/submission.csv", StandardCharsets.UTF_8));
            BufferedWriter bwScore = new BufferedWriter(new FileWriter("data/score.csv", StandardCharsets.UTF_8));
            BufferedWriter bwAnal = new BufferedWriter(new FileWriter("data/analysis_result.csv", StandardCharsets.UTF_8));
            BufferedWriter bwVio = new BufferedWriter(new FileWriter("data/rule_violation.csv", StandardCharsets.UTF_8))
        ) {
            bwSub.write("submissionId,userId,fileUrl,fileHash,status,submittedAt\n");
            bwScore.write("scoreId,submissionId,totalScore,deduction,gradingNotes,updatedAt\n");
            bwAnal.write("analysisId,submissionId,astJson,aiFeedback,executionTimeMs\n");
            bwVio.write("violationId,analysisId,ruleId,fileName,lineNumber,suggestedFix\n");

            for(int i=1; i<=200; i++) { // 200 submissions
                String subId = "SUB" + String.format("%04d", i);
                String uId = userIds.get(10 + rand.nextInt(90)); // Only students
                bwSub.write(String.format("%s,%s,http://git.com/%s,hash%s,Graded,2023-10-20\n", subId, uId, subId, subId));
                
                float deduction = rand.nextInt(4) * 0.5f;
                float total = 10.0f - deduction;
                bwScore.write(String.format("%d,%s,%.1f,%.1f,Good job,2023-10-21\n", i, subId, total, deduction));
                
                bwAnal.write(String.format("%d,%s,{},AI looks good,150\n", i, subId));
                
                if (deduction > 0) {
                    int rId = 1 + rand.nextInt(3);
                    bwVio.write(String.format("%d,%d,%d,Main.java,%d,Fix this issue\n", violationId++, i, rId, 10 + rand.nextInt(100)));
                }
            }
        } catch (Exception e) {}
        System.out.println("Generated 6 CSV files matching new ERD.");
    }
}
