package com.group1.util;

import com.group1.model.AitaRecord;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVHelper {

    public static List<AitaRecord> readRecordsFromCSV(String filePath) {
        List<AitaRecord> records = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); // Bỏ qua Header

            while ((line = br.readLine()) != null) {
                // Sử dụng dấu phẩy làm phân cách. Lưu ý: Cần xử lý nếu data có chứa dấu phẩy bên trong.
                String[] data = line.split(cvsSplitBy, -1);
                
                if(data.length >= 7) {
                    String userId = data[0].trim();
                    String name = data[1].trim();
                    String email = data[2].trim();
                    int age = data[3].trim().isEmpty() ? 0 : Integer.parseInt(data[3].trim());
                    String role = data[4].trim();
                    String submissionId = data[5].trim();
                    String analystId = data[6].trim();
                    
                    records.add(new AitaRecord(userId, name, email, age, role, submissionId, analystId));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return records;
    }
}
