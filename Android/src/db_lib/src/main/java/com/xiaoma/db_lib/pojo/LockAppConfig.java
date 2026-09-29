package com.xiaoma.db_lib.pojo;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Stores the password generator's single session-lock timeout configuration. */
@Entity(tableName = "lock_app_config")
public class LockAppConfig {
    /** Auto-generated database row ID. */
    @PrimaryKey(autoGenerate = true)
    public long lId;

    /** Human-readable timeout configuration label; defaults to 15 minutes. */
    public String configName = "15";
    /**
     * 0 立即
     * 1 1分钟
     * 15 15分钟
     * 30 30分钟
     * 60 1小时
     * 240 4小时
     * 1000 App重启
     * -1 从不
     */
    public int lockType = 15;
    /** Epoch time in milliseconds when this configuration was last applied. */
    public long lastTime;

    /** Epoch time in milliseconds when this row was last updated. */
    public long updateTime;

    /** Creates a 15-minute configuration timestamped with the current wall-clock time. */
    public LockAppConfig() {
        this.lastTime = System.currentTimeMillis();
        this.updateTime = lastTime;
    }
}
