package com.xiaoma.db_lib;


import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.xiaoma.db_lib.dao.LockAppConfigDao;
import com.xiaoma.db_lib.dao.MainPasswordDao;
import com.xiaoma.db_lib.dao.PasswordDao;
import com.xiaoma.db_lib.pojo.LockAppConfig;
import com.xiaoma.db_lib.pojo.MainPassword;
import com.xiaoma.db_lib.pojo.Password;

/**
 * Room database copied from the password generator project as a database-layer reference.
 *
 * <p>The contained entities belong to the password generator domain and are not the VoxMate
 * schema. Callers must perform DAO operations off the main thread.
 */
@Database(entities = {Password.class, MainPassword.class, LockAppConfig.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    /** Returns the DAO for stored password records. */
    public abstract PasswordDao passwordDao();

    /** Returns the DAO for the current primary password record. */
    public abstract MainPasswordDao mainPasswordDao();

    /** Returns the DAO for the single application-lock configuration record. */
    public abstract LockAppConfigDao lockAppConfigDao();
}
