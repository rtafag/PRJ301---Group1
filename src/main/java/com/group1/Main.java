package com.group1;

import com.group1.dao.AitaRecordDAO;
import com.group1.dao.AitaRecordDAOImpl;
import com.group1.model.AitaRecord;
import com.group1.util.CSVHelper;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== KẾT NỐI TỚI AITA_DB (SQL SERVER) & ĐỌC DỮ LIỆU TỪ CSV ===");
        
        AitaRecordDAO dao = new AitaRecordDAOImpl();
        
        // Đường dẫn tới file CSV 
        String csvFile = "data/records.csv";
        
        System.out.println("1. Đọc dữ liệu từ file CSV: " + csvFile);
        List<AitaRecord> newRecords = CSVHelper.readRecordsFromCSV(csvFile);
        
        System.out.println("2. Đưa dữ liệu từ CSV vào Database (AITA_DB)...");
        // Bỏ comment dòng dưới nếu muốn reset bảng mỗi lần chạy
        // dao.truncateTable(); 
        
        for (AitaRecord r : newRecords) {
            dao.insert(r);
            System.out.println(" -> Đã thêm: [" + r.getRole() + "] " + r.getName());
        }
        
        System.out.println("\n3. Lấy toàn bộ danh sách từ SQL Server hiển thị trực tiếp:");
        List<AitaRecord> dbRecords = dao.getAll();
        for (AitaRecord r : dbRecords) {
            System.out.println(r);
        }
    }
}
