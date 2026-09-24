package com.group1.model;

public class AitaRecord {
    private int dbId;
    private String userId; 
    private String name;
    private String email;
    private int age;
    private String role; 
    private String status; // active or offline
    private String courseCode;
    private double gpa; // for students

    public AitaRecord() {}

    public AitaRecord(String userId, String name, String email, int age, String role, String status, String courseCode, double gpa) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.age = age;
        this.role = role;
        this.status = status;
        this.courseCode = courseCode;
        this.gpa = gpa;
    }

    public AitaRecord(int dbId, String userId, String name, String email, int age, String role, String status, String courseCode, double gpa) {
        this.dbId = dbId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.age = age;
        this.role = role;
        this.status = status;
        this.courseCode = courseCode;
        this.gpa = gpa;
    }

    public int getDbId() { return dbId; }
    public void setDbId(int dbId) { this.dbId = dbId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public double getGpa() { return gpa; }
    public void setGpa(double gpa) { this.gpa = gpa; }

    @Override
    public String toString() {
        return "AitaRecord{userId='" + userId + "', name='" + name + 
               "', role='" + role + "', courseCode='" + courseCode + "', gpa=" + gpa + "}";
    }
}
