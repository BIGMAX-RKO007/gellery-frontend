package com.xiaoma.db_lib.pojo;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Stores one primary-password record for the imported password generator domain. */
@Entity(tableName = "main_password_table")
public class MainPassword {
    /** Auto-generated database row ID. */
    @PrimaryKey(autoGenerate = true)
    public long pId;

    /** Primary-password value; the imported schema does not encrypt this field. */
    public String password;

    /** Optional user-facing hint associated with the primary password. */
    public String hint;

    /** Creation time as Unix epoch milliseconds. */
    public long createTime;

    /** Last update time as Unix epoch milliseconds. */
    public long updateTime;

    /** Creates a record whose creation and update times use the current wall clock. */
    public MainPassword() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = createTime;
    }
}
