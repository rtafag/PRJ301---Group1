package com.group1.model;

public class AitaRecord {
    private int dbId;
    private String userId; 
    private String name;
    private String email;
    private int age;
    private String role; 
    private String submissionId;
    private String status; // active or offline

    public AitaRecord() {}

    public AitaRecord(String userId, String name, String email, int age, String role, String submissionId, String status) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.age = age;
        this.role = role;
        this.submissionId = submissionId;
        this.status = status;
    }

    public AitaRecord(int dbId, String userId, String name, String email, int age, String role, String submissionId, String status) {
        this.dbId = dbId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.age = age;
        this.role = role;
        this.submissionId = submissionId;
        this.status = status;
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
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "AitaRecord{userId='" + userId + "', name='" + name + 
               "', role='" + role + "', status='" + status + "', submissionId='" + submissionId + "'}";
    }
}
