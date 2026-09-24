package com.group1.dao;

import com.group1.model.UserAccount;
import java.util.List;

public interface UserAccountDAO {
    void insert(UserAccount account);
    UserAccount getById(String userId);
    List<UserAccount> getAll();
    void truncateTable(); 
}
