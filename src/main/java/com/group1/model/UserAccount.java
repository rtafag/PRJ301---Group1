package com.group1.model;

public class UserAccount {
    private String userId;
    private String password;

    public UserAccount() {}

    public UserAccount(String userId, String password) {
        this.userId = userId;
        this.password = password;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    @Override
    public String toString() {
        return "UserAccount{userId='" + userId + "', password='***'}";
    }
}
