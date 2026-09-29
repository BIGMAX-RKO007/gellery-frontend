package com.xiaoma.db_lib.pojo;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Stores one account credential entry in the imported password generator schema. */
@Entity(tableName = "password_table")
public class Password {
    /** Auto-generated database row ID. */
    @PrimaryKey(autoGenerate = true)
    public long pId;
    /**
     * 名称
     */
    public String pName;
    /**
     * 密码
     */
    public String password;
    /**
     * 账号
     */
    public String account;
    /**
     * 备注
     */
    public String note;
    /**
     * 标签
     */
    public String label;

    /** Creation time as Unix epoch milliseconds. */
    public long createTime;

    /** Last update time as Unix epoch milliseconds and the primary paging cursor. */
    public long updateTime;

    /** Creates an entry whose creation and update times use the current wall clock. */
    public Password() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = createTime;
    }
}
