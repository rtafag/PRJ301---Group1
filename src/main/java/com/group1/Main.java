package com.group1;

import com.group1.dao.AitaRecordDAO;
import com.group1.dao.AitaRecordDAOImpl;
import com.group1.dao.SubmissionDAO;
import com.group1.dao.SubmissionDAOImpl;
import com.group1.model.AitaRecord;
import com.group1.model.Submission;
import com.group1.util.CSVHelper;

import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== KẾT NỐI TỚI AITA_DB & NHẬP DỮ LIỆU TỪ 3 FILE CSV ===");
        
        AitaRecordDAO userDAO = new AitaRecordDAOImpl();
        SubmissionDAO submissionDAO = new SubmissionDAOImpl();
        
        String studentsFile = "data/students.csv";
        String teachersFile = "data/teachers.csv";
        String submissionsFile = "data/submissions.csv";
        
        // --- 1. NHẬP DỮ LIỆU SINH VIÊN ---
        System.out.println("\n--- Đang đọc dữ liệu Sinh viên từ: " + studentsFile + " ---");
        List<AitaRecord> students = CSVHelper.readStudentsFromCSV(studentsFile);
        for (AitaRecord r : students) {
            userDAO.insert(r);
            System.out.println(" -> Đã thêm Sinh viên: [" + r.getStatus() + "] " + r.getName());
        }

        // --- 2. NHẬP DỮ LIỆU GIẢNG VIÊN ---
        System.out.println("\n--- Đang đọc dữ liệu Giảng viên từ: " + teachersFile + " ---");
        List<AitaRecord> teachers = CSVHelper.readTeachersFromCSV(teachersFile);
        for (AitaRecord r : teachers) {
            userDAO.insert(r);
            System.out.println(" -> Đã thêm Giảng viên: [" + r.getStatus() + "] " + r.getName());
        }

        // --- 3. NHẬP DỮ LIỆU SUBMISSION ---
        System.out.println("\n--- Đang đọc dữ liệu Bài nộp từ: " + submissionsFile + " ---");
        List<Submission> submissions = CSVHelper.readSubmissionsFromCSV(submissionsFile);
        for (Submission s : submissions) {
            submissionDAO.insert(s);
            System.out.println(" -> Đã thêm Submission: " + s.getSubmissionId() + " (Điểm: " + s.getScore() + ")");
        }
        
        // --- 4. KIỂM THỬ: GIẢNG VIÊN CHẤM VÀ SỬA ĐIỂM ---
        System.out.println("\n--- KIỂM THỬ QUYỀN GIẢNG VIÊN CHẤM ĐIỂM ---");
        // Giả sử có một Giảng viên GV001234 muốn sửa điểm bài SUB001
        String graderId = "GV001234";
        String targetSubmissionId = "SUB001";
        double newScore = 10.0;
        String newPublicTime = "2023-10-22 08:00:00";
        
        // Kiểm tra xem graderId có phải là Giảng viên không?
        List<AitaRecord> allUsersForCheck = userDAO.getAll();
        Optional<AitaRecord> graderOpt = allUsersForCheck.stream()
                .filter(u -> u.getUserId().equals(graderId))
                .findFirst();
                
        if (graderOpt.isPresent()) {
            AitaRecord grader = graderOpt.get();
            if ("Giảng viên".equalsIgnoreCase(grader.getRole())) {
                System.out.println("[Cho phép] Tài khoản " + grader.getName() + " (" + grader.getRole() + ") có quyền chấm điểm.");
                submissionDAO.updateScore(targetSubmissionId, newScore, newPublicTime);
                System.out.println(" -> Đã cập nhật điểm bài nộp " + targetSubmissionId + " thành " + newScore);
            } else {
                System.out.println("[Từ chối] Tài khoản " + grader.getName() + " không có quyền Giảng viên!");
            }
        } else {
            System.out.println("[Lỗi] Không tìm thấy tài khoản " + graderId);
        }

        // --- 5. HIỂN THỊ KẾT QUẢ TỪ DATABASE ---
        System.out.println("\n=== KẾT QUẢ TỪ SQL SERVER ===");
        System.out.println("--- Bảng aita_records (Sinh viên & Giảng viên) ---");
        List<AitaRecord> allUsers = userDAO.getAll();
        for (AitaRecord r : allUsers) {
            System.out.println(r);
        }

        System.out.println("\n--- Bảng submissions (Bài nộp) ---");
        List<Submission> allSubmissions = submissionDAO.getAll();
        for (Submission s : allSubmissions) {
            System.out.println(s);
        }
    }
}
