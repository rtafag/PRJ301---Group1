package com.group1.dao;

import com.group1.model.AitaRecord;
import java.util.List;

public interface AitaRecordDAO {
    void insert(AitaRecord record);
    AitaRecord getById(int dbId);
    List<AitaRecord> getAll();
    void update(AitaRecord record);
    void delete(int dbId);
    void truncateTable(); // Dùng để xóa bảng khi import lại từ CSV
}
