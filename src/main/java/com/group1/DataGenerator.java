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
    
    public static String generateRandomPassword(Random rand) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder(8);
        for(int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
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
        
        // 10 realistic courses
        String[] COURSES = {"PRJ301", "SWP391", "CSD201", "MAS291", "PRO192", "MAE101", "CEA201", "JPD113", "JPE255", "NWC203"};

        List<String> userAccounts = new ArrayList<>();

        // 1. Giang vien (100 rows) - Mỗi người 1-3 mã môn
        List<String> teacherIds = new ArrayList<>();
        List<List<String>> teacherCoursesMap = new ArrayList<>(); // To store what each teacher teaches
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/teachers.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Name,Email,Age,Role,Status,CourseCode\n");
            for (int i = 1; i <= 100; i++) {
                String userId = String.format("GV%06d", i);
                teacherIds.add(userId);
                
                String name = allNames.get(nameIdx++);
                String prefix = generateEmailPrefix(name, rand);
                String email = prefix + "@fpt.edu.vn";
                String username = prefix;
                String password = generateRandomPassword(rand);
                int age = 30 + rand.nextInt(30);
                String role = "Giảng viên";
                String status = rand.nextBoolean() ? "active" : "offline";
                
                userAccounts.add(String.format("%s,%s,%s,%s,%s,%s", userId, username, email, role, status, password));
                
                // Random 1 to 3 courses
                int numCourses = 1 + rand.nextInt(3);
                List<String> tCourses = new ArrayList<>();
                while (tCourses.size() < numCourses) {
                    String c = COURSES[rand.nextInt(COURSES.length)];
                    if (!tCourses.contains(c)) tCourses.add(c);
                }
                teacherCoursesMap.add(tCourses);
                String courseStr = String.join("|", tCourses);
                
                bw.write(String.format("%s,%s,%s,%d,%s,%s,%s\n", userId, name, email, age, role, status, courseStr));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Sinh vien (1000 rows) - Mỗi sinh viên 3-5 mã môn
        List<String> studentIds = new ArrayList<>();
        List<List<String>> studentCoursesMap = new ArrayList<>();
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/students.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Name,Email,Age,Role,Status,CourseCode,GPA\n");
            
            for (int i = 1; i <= 1000; i++) {
                String userId = String.format("HE15%04d", i);
                studentIds.add(userId);
                
                String name = allNames.get(nameIdx++);
                String prefix = generateEmailPrefix(name, rand);
                String email = prefix + (rand.nextBoolean() ? "@gmail.com" : "@fpt.edu.vn");
                String username = prefix;
                String password = generateRandomPassword(rand);
                
                int age = 18 + rand.nextInt(7);
                String role = "Sinh viên";
                String status = rand.nextBoolean() ? "active" : "offline";
                double gpa = Math.round((4.0 + rand.nextDouble() * 6.0) * 10.0) / 10.0;
                
                userAccounts.add(String.format("%s,%s,%s,%s,%s,%s", userId, username, email, role, status, password));
                
                // Random 3 to 5 courses
                int numCourses = 3 + rand.nextInt(3);
                List<String> sCourses = new ArrayList<>();
                while (sCourses.size() < numCourses) {
                    String c = COURSES[rand.nextInt(COURSES.length)];
                    if (!sCourses.contains(c)) sCourses.add(c);
                }
                studentCoursesMap.add(sCourses);
                String courseStr = String.join("|", sCourses);
                
                bw.write(String.format("%s,%s,%s,%d,%s,%s,%s,%.1f\n", userId, name, email, age, role, status, courseStr, gpa));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 3. Users.csv
        Collections.shuffle(userAccounts);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/users.csv", StandardCharsets.UTF_8))) {
            bw.write("UserID,Username,Email,Role,Status,Password\n");
            for (String acc : userAccounts) {
                bw.write(acc + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 4. Submissions (2000 rows - ~2 submissions per student)
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("data/submissions.csv", StandardCharsets.UTF_8))) {
            bw.write("SubmissionID,StudentID,TeacherID,SubmitTime,Score,ScorePublicTime\n");
            int subIdCounter = 1;
            
            for (int i = 0; i < studentIds.size(); i++) {
                String studentId = studentIds.get(i);
                List<String> sCourses = studentCoursesMap.get(i);
                
                // Generate 1 to 3 submissions for this student
                int numSubmissions = 1 + rand.nextInt(3);
                for (int s = 0; s < numSubmissions; s++) {
                    String subId = String.format("SUB%04d", subIdCounter++);
                    
                    // Pick a random course the student is taking
                    String chosenCourse = sCourses.get(rand.nextInt(sCourses.size()));
                    
                    // Find all teachers who teach this course
                    List<String> eligibleTeachers = new ArrayList<>();
                    for (int t = 0; t < teacherIds.size(); t++) {
                        if (teacherCoursesMap.get(t).contains(chosenCourse)) {
                            eligibleTeachers.add(teacherIds.get(t));
                        }
                    }
                    
                    String teacherId = "";
                    if (!eligibleTeachers.isEmpty()) {
                        teacherId = eligibleTeachers.get(rand.nextInt(eligibleTeachers.size()));
                    } else {
                        teacherId = teacherIds.get(rand.nextInt(teacherIds.size())); 
                    }
                    
                    String submitTime = "2023-10-20 10:00:00";
                    double score = Math.round(rand.nextDouble() * 100.0) / 10.0;
                    String scorePublicTime = "2023-10-21 15:00:00";
                    
                    bw.write(String.format("%s,%s,%s,%s,%.1f,%s\n", subId, studentId, teacherId, submitTime, score, scorePublicTime));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Data generated successfully with full Users table (random passwords)!");
    }
}
