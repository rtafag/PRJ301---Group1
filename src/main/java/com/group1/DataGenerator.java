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
    private static final String[] LAST_NAMES = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"};
    private static final String[] MIDDLE_NAMES = {"Văn", "Thị", "Hữu", "Ngọc", "Đức", "Minh", "Quang", "Xuân", "Thu", "Thanh", "Hoàng", "Gia", "Thành", "Đình", "Hải"};
    private static final String[] FIRST_NAMES = {"Anh", "Tuấn", "Dũng", "Hoa", "Lan", "Hương", "Huy", "Cường", "Trang", "Linh", "Phong", "Nga", "Sơn", "Tùng", "Bình", "Thảo", "Hà", "Vy", "Long", "Bách", "Khoa", "Nguyên", "Khánh"};

    public static String removeAccents(String s) {
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                         .replaceAll("Đ", "D").replaceAll("đ", "d")
                         .toLowerCase().replaceAll("\\s+", "");
    }

    public static String generateEmailPrefix(String fullName, Random rand) {
        String[] parts = fullName.split(" ");
        String firstName = parts[parts.length - 1];
        String lastName = parts[0];
        
        String prefix = removeAccents(firstName + lastName) + rand.nextInt(1000);
        return prefix;
    }

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
        
        List<String> courseCodes = new ArrayList<>();
        for(int i = 1; i <= 100; i++) {
            courseCodes.add(String.format("PRJ%03d", i));
        }

        List<String> userAccounts = new ArrayList<>();

        // 1. Giang vien (100 rows) - Mỗi người 1 mã môn
        List<String> teacherIds = new ArrayList<>();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/teachers.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Name,Email,Age,Role,Status,CourseCode\n");
            for (int i = 1; i <= 100; i++) {
                String userId = String.format("GV%06d", i);
                teacherIds.add(userId);
                userAccounts.add(userId + ",password123");
                
                String name = allNames.get(nameIdx++);
                String email = generateEmailPrefix(name, rand) + "@fpt.edu.vn";
                int age = 30 + rand.nextInt(30);
                String role = "Giảng viên";
                String status = rand.nextBoolean() ? "active" : "offline";
                String course = courseCodes.get(i - 1);
                
                bw.write(String.format("%s,%s,%s,%d,%s,%s,%s\n", userId, name, email, age, role, status, course));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Sinh vien (1000 rows) - Mỗi mã môn có 10 SV
        List<String> studentIds = new ArrayList<>();
        List<String> studentTeacherMap = new ArrayList<>();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/students.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Name,Email,Age,Role,Status,CourseCode,GPA\n");
            
            int studentCount = 1;
            for (int i = 0; i < 100; i++) {
                String course = courseCodes.get(i);
                String teacherIdForCourse = teacherIds.get(i); // Since 1-to-1 course-to-teacher mapping
                
                for (int j = 0; j < 10; j++) {
                    String userId = String.format("HE15%04d", studentCount++);
                    studentIds.add(userId);
                    studentTeacherMap.add(teacherIdForCourse); // save for submission mapping
                    userAccounts.add(userId + ",password123");
                    
                    String name = allNames.get(nameIdx++);
                    String email = generateEmailPrefix(name, rand) + (rand.nextBoolean() ? "@gmail.com" : "@fpt.edu.vn");
                    int age = 18 + rand.nextInt(7);
                    String role = "Sinh viên";
                    String status = rand.nextBoolean() ? "active" : "offline";
                    double gpa = Math.round((4.0 + rand.nextDouble() * 6.0) * 10.0) / 10.0; // 4.0 to 10.0
                    
                    bw.write(String.format("%s,%s,%s,%d,%s,%s,%s,%.1f\n", userId, name, email, age, role, status, course, gpa));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 3. Users.csv
        Collections.shuffle(userAccounts);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/users.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Password\n");
            for (String acc : userAccounts) {
                bw.write(acc + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 4. Submissions (1000 rows)
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/submissions.csv", StandardCharsets.UTF_8))) {
            bw.write("SubmissionID,StudentID,TeacherID,SubmitTime,Score,ScorePublicTime\n");
            for (int i = 0; i < 1000; i++) {
                String subId = String.format("SUB%04d", i + 1);
                String studentId = studentIds.get(i);
                String teacherId = studentTeacherMap.get(i); // Assign correctly to the teacher of that course
                String submitTime = "2023-10-20 10:00:00";
                double score = Math.round(rand.nextDouble() * 100.0) / 10.0;
                String scorePublicTime = "2023-10-21 15:00:00";
                bw.write(String.format("%s,%s,%s,%s,%.1f,%s\n", subId, studentId, teacherId, submitTime, score, scorePublicTime));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Data generated successfully with realistic emails, course mappings, GPA and users.csv!");
    }
}
