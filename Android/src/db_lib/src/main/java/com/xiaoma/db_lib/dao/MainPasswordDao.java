package com.xiaoma.db_lib.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.xiaoma.db_lib.pojo.MainPassword;

/** Provides persistence operations for the password generator's primary-password record. */
@Dao
public interface MainPasswordDao {
    /** Inserts a primary-password record and returns its generated row ID. */
    @Insert
    Long insert(MainPassword mainPassword);

    /** Updates the matching primary-password row and returns the number of changed rows. */
    @Update
    int update(MainPassword mainPassword);

    /** Returns the most recently inserted primary-password record, or {@code null} if empty. */
    @Query("SELECT * FROM main_password_table ORDER BY pId DESC LIMIT 1")
    MainPassword getLatestMainPassword();

}
