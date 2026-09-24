package com.group1;

import com.group1.dao.StudentDAO;
import com.group1.dao.StudentDAOImpl;
import com.group1.model.Student;
import com.group1.util.CSVHelper;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== KẾT NỐI TỚI AITA_DB (SQL SERVER) & ĐỌC DỮ LIỆU TỪ CSV ===");
        
        StudentDAO studentDAO = new StudentDAOImpl();
        
        // Đường dẫn tới file CSV 
        String csvFile = "data/students.csv";
        
        System.out.println("1. Đọc dữ liệu từ file CSV: " + csvFile);
        List<Student> newStudents = CSVHelper.readStudentsFromCSV(csvFile);
        
        System.out.println("2. Đưa dữ liệu từ CSV vào Database (AITA_DB)...");
        // Xóa bảng cũ (chỉ dùng cho mục đích demo để dữ liệu không bị trùng lặp khi chạy nhiều lần)
        // studentDAO.truncateTable(); 
        
        for (Student s : newStudents) {
            studentDAO.insert(s);
            System.out.println(" -> Đã thêm: " + s.getName());
        }
        
        System.out.println("\n3. Lấy toàn bộ danh sách từ SQL Server hiển thị trực tiếp:");
        List<Student> dbStudents = studentDAO.getAll();
        for (Student s : dbStudents) {
            System.out.println(s);
        }
    }
}
