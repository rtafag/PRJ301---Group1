package com.group1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DataGenerator {
    private static final String[] LAST_NAMES = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"};
    private static final String[] MIDDLE_NAMES = {"Văn", "Thị", "Hữu", "Ngọc", "Đức", "Minh", "Quang", "Xuân", "Thu", "Thanh", "Hoàng", "Gia", "Thành", "Đình", "Hải"};
    private static final String[] FIRST_NAMES = {"Anh", "Tuấn", "Dũng", "Hoa", "Lan", "Hương", "Huy", "Cường", "Trang", "Linh", "Phong", "Nga", "Sơn", "Tùng", "Bình", "Thảo", "Hà", "Vy", "Long", "Bách", "Khoa", "Nguyên", "Khánh"};

    public static void main(String[] args) {
        Random rand = new Random();
        Set<String> usedNames = new HashSet<>();
        
        List<String> allNames = new ArrayList<>();
        while(allNames.size() < 1100) {
            String name = LAST_NAMES[rand.nextInt(LAST_NAMES.length)] + " " + 
                          MIDDLE_NAMES[rand.nextInt(MIDDLE_NAMES.length)] + " " + 
                          FIRST_NAMES[rand.nextInt(FIRST_NAMES.length)];
            if(usedNames.add(name)) {
                allNames.add(name);
            }
        }
        Collections.shuffle(allNames);
        
        int nameIdx = 0;

        // 1. Sinh vien (1000 rows)
        List<String> studentIds = new ArrayList<>();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/students.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Name,Email,Age,Role,Status\n");
            for (int i = 1; i <= 1000; i++) {
                String userId = String.format("HE15%04d", i);
                studentIds.add(userId);
                String name = allNames.get(nameIdx++);
                String email = "sv" + i + "@fpt.edu.vn";
                int age = 18 + rand.nextInt(7);
                String role = "Sinh viên";
                String status = rand.nextBoolean() ? "active" : "offline";
                bw.write(String.format("%s,%s,%s,%d,%s,%s\n", userId, name, email, age, role, status));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Giang vien (100 rows)
        List<String> teacherIds = new ArrayList<>();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/teachers.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Name,Email,Age,Role,Status\n");
            for (int i = 1; i <= 100; i++) {
                String userId = String.format("GV%06d", i);
                teacherIds.add(userId);
                String name = allNames.get(nameIdx++);
                String email = "gv" + i + "@fpt.edu.vn";
                int age = 30 + rand.nextInt(30);
                String role = "Giảng viên";
                String status = rand.nextBoolean() ? "active" : "offline";
                bw.write(String.format("%s,%s,%s,%d,%s,%s\n", userId, name, email, age, role, status));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3. Submissions (1000 rows)
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/submissions.csv", StandardCharsets.UTF_8))) {
            bw.write("SubmissionID,StudentID,TeacherID,SubmitTime,Score,ScorePublicTime\n");
            for (int i = 1; i <= 1000; i++) {
                String subId = String.format("SUB%04d", i);
                String studentId = studentIds.get(i-1);
                String teacherId = teacherIds.get(rand.nextInt(teacherIds.size()));
                String submitTime = "2023-10-20 10:00:00";
                double score = Math.round(rand.nextDouble() * 100.0) / 10.0;
                String scorePublicTime = "2023-10-21 15:00:00";
                bw.write(String.format("%s,%s,%s,%s,%.1f,%s\n", subId, studentId, teacherId, submitTime, score, scorePublicTime));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Data generated successfully with unique Vietnamese names!");
    }
}
