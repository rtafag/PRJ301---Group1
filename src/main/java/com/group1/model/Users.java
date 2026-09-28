package com.group1.model;
public class Users {
    public String userId; public String username; public String passwordHash; 
    public String fullName; public String email; public String role; public String createdAt;
    public Users(String userId, String username, String passwordHash, String fullName, String email, String role, String createdAt) {
        this.userId=userId; this.username=username; this.passwordHash=passwordHash; 
        this.fullName=fullName; this.email=email; this.role=role; this.createdAt=createdAt;
    }
}
