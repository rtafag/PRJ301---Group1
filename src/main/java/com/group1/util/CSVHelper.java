package com.group1.util;

import com.group1.model.AitaRecord;
import com.group1.model.Submission;
import com.group1.model.UserAccount;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVHelper {

    public static List<UserAccount> readUsersFromCSV(String filePath) {
        List<UserAccount> accounts = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy, -1);
                if(data.length >= 2) {
                    accounts.add(new UserAccount(data[0].trim(), data[1].trim()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return accounts;
    }

    public static List<AitaRecord> readStudentsFromCSV(String filePath) {
        List<AitaRecord> records = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy, -1);
                if(data.length >= 8) {
                    String userId = data[0].trim();
                    String name = data[1].trim();
                    String email = data[2].trim();
                    int age = data[3].trim().isEmpty() ? 0 : Integer.parseInt(data[3].trim());
                    String role = data[4].trim();
                    String status = data[5].trim();
                    String courseCode = data[6].trim();
                    double gpa = data[7].trim().isEmpty() ? 0.0 : Double.parseDouble(data[7].trim());
                    
                    records.add(new AitaRecord(userId, name, email, age, role, status, courseCode, gpa));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return records;
    }

    public static List<AitaRecord> readTeachersFromCSV(String filePath) {
        List<AitaRecord> records = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy, -1);
                if(data.length >= 7) {
                    String userId = data[0].trim();
                    String name = data[1].trim();
                    String email = data[2].trim();
                    int age = data[3].trim().isEmpty() ? 0 : Integer.parseInt(data[3].trim());
                    String role = data[4].trim();
                    String status = data[5].trim();
                    String courseCode = data[6].trim();
                    
                    records.add(new AitaRecord(userId, name, email, age, role, status, courseCode, 0.0));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return records;
    }

    public static List<Submission> readSubmissionsFromCSV(String filePath) {
        List<Submission> submissions = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine(); 
            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy, -1);
                if(data.length >= 6) {
                    String submissionId = data[0].trim();
                    String studentId = data[1].trim();
                    String teacherId = data[2].trim();
                    String submitTime = data[3].trim();
                    double score = data[4].trim().isEmpty() ? 0.0 : Double.parseDouble(data[4].trim());
                    String scorePublicTime = data[5].trim();
                    
                    submissions.add(new Submission(submissionId, studentId, teacherId, submitTime, score, scorePublicTime));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return submissions;
    }
}
