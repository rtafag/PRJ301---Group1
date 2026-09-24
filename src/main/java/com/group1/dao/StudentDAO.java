package com.group1.dao;

import com.group1.model.Student;
import java.util.List;

public interface StudentDAO {
    void insert(Student student);
    Student getById(int id);
    List<Student> getAll();
    void update(Student student);
    void delete(int id);
    void truncateTable(); // Useful for testing with CSV import
}
