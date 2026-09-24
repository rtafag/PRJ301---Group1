package com.group1;

import com.group1.dao.AitaRecordDAO;
import com.group1.dao.AitaRecordDAOImpl;
import com.group1.dao.SubmissionDAO;
import com.group1.dao.SubmissionDAOImpl;
import com.group1.model.AitaRecord;
import com.group1.model.Submission;
import com.group1.util.CSVHelper;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== KẾT NỐI TỚI AITA_DB & NHẬP DỮ LIỆU TỪ 3 FILE CSV ===");
        
        AitaRecordDAO userDAO = new AitaRecordDAOImpl();
        SubmissionDAO submissionDAO = new SubmissionDAOImpl();
        
        // Đường dẫn file CSV 
        String studentsFile = "data/students.csv";
        String teachersFile = "data/teachers.csv";
        String submissionsFile = "data/submissions.csv";
        
        // --- 1. NHẬP DỮ LIỆU HỌC SINH ---
        System.out.println("\n--- Đang đọc dữ liệu Học sinh từ: " + studentsFile + " ---");
        List<AitaRecord> students = CSVHelper.readUsersFromCSV(studentsFile);
        for (AitaRecord r : students) {
            userDAO.insert(r);
            System.out.println(" -> Đã thêm Học sinh: [" + r.getStatus() + "] " + r.getName());
        }

        // --- 2. NHẬP DỮ LIỆU GIÁO VIÊN ---
        System.out.println("\n--- Đang đọc dữ liệu Giáo viên từ: " + teachersFile + " ---");
        List<AitaRecord> teachers = CSVHelper.readUsersFromCSV(teachersFile);
        for (AitaRecord r : teachers) {
            userDAO.insert(r);
            System.out.println(" -> Đã thêm Giáo viên: [" + r.getStatus() + "] " + r.getName());
        }

        // --- 3. NHẬP DỮ LIỆU SUBMISSION ---
        System.out.println("\n--- Đang đọc dữ liệu Bài nộp từ: " + submissionsFile + " ---");
        List<Submission> submissions = CSVHelper.readSubmissionsFromCSV(submissionsFile);
        for (Submission s : submissions) {
            submissionDAO.insert(s);
            System.out.println(" -> Đã thêm Submission: " + s.getSubmissionId() + " (Điểm: " + s.getScore() + ")");
        }
        
        // --- 4. HIỂN THỊ KẾT QUẢ TỪ DATABASE ---
        System.out.println("\n=== KẾT QUẢ TỪ SQL SERVER ===");
        System.out.println("--- Bảng aita_records (Học sinh & Giáo viên) ---");
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
