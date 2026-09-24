package com.group1;

import com.group1.dao.AitaRecordDAO;
import com.group1.dao.AitaRecordDAOImpl;
import com.group1.dao.SubmissionDAO;
import com.group1.dao.SubmissionDAOImpl;
import com.group1.dao.UserAccountDAO;
import com.group1.dao.UserAccountDAOImpl;
import com.group1.model.AitaRecord;
import com.group1.model.Submission;
import com.group1.model.UserAccount;
import com.group1.util.CSVHelper;

import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== KẾT NỐI TỚI AITA_DB & NHẬP DỮ LIỆU TỪ 4 FILE CSV ===");
        
        AitaRecordDAO aitaDAO = new AitaRecordDAOImpl();
        SubmissionDAO submissionDAO = new SubmissionDAOImpl();
        UserAccountDAO userAccountDAO = new UserAccountDAOImpl();
        
        String usersFile = "data/users.csv";
        String studentsFile = "data/students.csv";
        String teachersFile = "data/teachers.csv";
        String submissionsFile = "data/submissions.csv";
        
        // --- 1. NHẬP DỮ LIỆU USERS ---
        System.out.println("\n--- Đang đọc dữ liệu Tài khoản từ: " + usersFile + " ---");
        List<UserAccount> accounts = CSVHelper.readUsersFromCSV(usersFile);
        for (UserAccount acc : accounts) {
            userAccountDAO.insert(acc);
            System.out.println(" -> Đã thêm Tài khoản: " + acc.getUserId());
        }

        // --- 2. NHẬP DỮ LIỆU SINH VIÊN ---
        System.out.println("\n--- Đang đọc dữ liệu Sinh viên từ: " + studentsFile + " ---");
        List<AitaRecord> students = CSVHelper.readStudentsFromCSV(studentsFile);
        for (AitaRecord r : students) {
            aitaDAO.insert(r);
            System.out.println(" -> Đã thêm Sinh viên: " + r.getName() + " - Môn: " + r.getCourseCode() + " - GPA: " + r.getGpa());
        }

        // --- 3. NHẬP DỮ LIỆU GIẢNG VIÊN ---
        System.out.println("\n--- Đang đọc dữ liệu Giảng viên từ: " + teachersFile + " ---");
        List<AitaRecord> teachers = CSVHelper.readTeachersFromCSV(teachersFile);
        for (AitaRecord r : teachers) {
            aitaDAO.insert(r);
            System.out.println(" -> Đã thêm Giảng viên: " + r.getName() + " - Dạy môn: " + r.getCourseCode());
        }

        // --- 4. NHẬP DỮ LIỆU SUBMISSION ---
        System.out.println("\n--- Đang đọc dữ liệu Bài nộp từ: " + submissionsFile + " ---");
        List<Submission> submissions = CSVHelper.readSubmissionsFromCSV(submissionsFile);
        for (Submission s : submissions) {
            submissionDAO.insert(s);
            System.out.println(" -> Đã thêm Submission: " + s.getSubmissionId() + " (Điểm: " + s.getScore() + ")");
        }
        
        System.out.println("\n=== HOÀN TẤT NẠP DỮ LIỆU ===");
    }
}
