package com.xiaoma.db_lib.dao;

import androidx.room.Dao;
import androidx.room.Insert;

import com.xiaoma.db_lib.pojo.LockAppConfig;

/** Provides atomic access to the single application-lock configuration row. */
@Dao
public abstract class LockAppConfigDao {

    /** Inserts a lock configuration and returns its generated row ID. */
    @Insert
    public abstract Long insert(LockAppConfig lockAppConfig);

    /** Deletes every lock configuration row so only the replacement can remain. */
    @androidx.room.Query("DELETE FROM lock_app_config")
    public abstract void deleteAll();

    /** Replaces all existing configuration rows with {@code lockAppConfig}. */
    @androidx.room.Transaction
    public Long insertOrUpdate(LockAppConfig lockAppConfig) {
        deleteAll();
        return insert(lockAppConfig);
    }

    /** Returns the single saved configuration, or {@code null} when none exists. */
    @androidx.room.Query("SELECT * FROM lock_app_config LIMIT 1")
    public abstract LockAppConfig getLockAppConfig();
}
