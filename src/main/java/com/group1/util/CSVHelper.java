package com.group1.util;

import com.group1.model.Student;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVHelper {

    // Đọc dữ liệu từ file CSV
    public static List<Student> readStudentsFromCSV(String filePath) {
        List<Student> students = new ArrayList<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Đọc dòng Header đầu tiên (nếu có) và bỏ qua
            br.readLine(); 

            while ((line = br.readLine()) != null) {
                // Sử dụng dấu phẩy làm phân cách
                String[] data = line.split(cvsSplitBy);
                
                if(data.length >= 3) {
                    String name = data[0].trim();
                    String email = data[1].trim();
                    int age = Integer.parseInt(data[2].trim());
                    
                    students.add(new Student(name, email, age));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return students;
    }
}
