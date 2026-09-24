package com.group1.util;

import com.group1.model.AitaRecord;
import com.group1.model.Submission;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVHelper {

    // Đọc danh sách user (học sinh / giáo viên) từ CSV
    public static List<AitaRecord> readUsersFromCSV(String filePath) {
        List<AitaRecord> records = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); // Bỏ qua Header

            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy, -1);
                
                if(data.length >= 8) {
                    String userId = data[0].trim();
                    String name = data[1].trim();
                    String email = data[2].trim();
                    int age = data[3].trim().isEmpty() ? 0 : Integer.parseInt(data[3].trim());
                    String role = data[4].trim();
                    String submissionId = data[5].trim();
                    String analystId = data[6].trim();
                    String status = data[7].trim(); // active or offline
                    
                    records.add(new AitaRecord(userId, name, email, age, role, submissionId, analystId, status));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return records;
    }

    // Đọc danh sách Submissions từ CSV
    public static List<Submission> readSubmissionsFromCSV(String filePath) {
        List<Submission> submissions = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); // Bỏ qua Header

            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy, -1);
                
                if(data.length >= 4) {
                    String submissionId = data[0].trim();
                    String submitTime = data[1].trim();
                    double score = data[2].trim().isEmpty() ? 0.0 : Double.parseDouble(data[2].trim());
                    String scorePublicTime = data[3].trim();
                    
                    submissions.add(new Submission(submissionId, submitTime, score, scorePublicTime));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return submissions;
    }
}
