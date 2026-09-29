package com.xiaoma.db_lib.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.xiaoma.db_lib.pojo.Password;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Provides ordered, cursor-paged CRUD operations for password generator entries. */
@Dao
public interface PasswordDao {
    /** Inserts {@code password} and returns its generated row ID. */
    @Insert
    Long insert(Password password);

    /** Returns all entries ordered from most recently updated to oldest. */
    @Query("SELECT * FROM password_table ORDER BY updateTime DESC, pId DESC")
    List<Password> findAll();

    /** Returns the first page with at most {@code limit} entries. */
    @Query("SELECT * FROM password_table ORDER BY updateTime DESC, pId DESC LIMIT :limit")
    List<Password> findFirstPage(int limit);

    /** Returns the page strictly after the supplied update-time and row-ID cursor. */
    @Query(
            "SELECT * FROM password_table " +
                    "WHERE updateTime < :cursorUpdateTime " +
                    "OR (updateTime = :cursorUpdateTime AND pId < :cursorId) " +
                    "ORDER BY updateTime DESC, pId DESC LIMIT :limit"
    )
    List<Password> findNextPage(long cursorUpdateTime, long cursorId, int limit);


    /** Updates the matching entry and returns the number of changed rows. */
    @Update
    int update(Password password);

    /** Returns every entry whose name or note contains {@code keyword}. */
    @Query("SELECT * FROM password_table WHERE pName LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%' ORDER BY updateTime DESC, pId DESC")
    List<Password> searchByKeyword(String keyword);

    /** Returns the first keyword-search page with at most {@code limit} entries. */
    @Query("SELECT * FROM password_table WHERE pName LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%' ORDER BY updateTime DESC, pId DESC LIMIT :limit")
    List<Password> searchFirstPageByKeyword(String keyword, int limit);

    /** Returns the keyword-search page strictly after the supplied cursor. */
    @Query(
            "SELECT * FROM password_table " +
                    "WHERE (pName LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%') " +
                    "AND (updateTime < :cursorUpdateTime OR (updateTime = :cursorUpdateTime AND pId < :cursorId)) " +
                    "ORDER BY updateTime DESC, pId DESC LIMIT :limit"
    )
    List<Password> searchNextPageByKeyword(String keyword, long cursorUpdateTime, long cursorId, int limit);

    /** Deletes the exact persisted entry represented by {@code passwordNode}. */
    @Delete
    void delete(@NotNull Password passwordNode);
}
