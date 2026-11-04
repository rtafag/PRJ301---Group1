package com.group1.model;

public final class SessionUser {
    private final String userId;
    private final String username;
    private final String fullName;
    private final String role;

    public SessionUser(String userId, String username, String fullName, String role) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
}
