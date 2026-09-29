package com.xiaoma.db_lib;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;

import java.util.concurrent.Executors;

/**
 * Process-local owner of the password generator reference database.
 *
 * <p>The singleton stores only a Room database created from the application context. Database
 * calls must be dispatched to a background executor or coroutine dispatcher by the caller.
 */
public final class DatabaseClient {
    /** Lazily initialized process-local client instance. */
    private static DatabaseClient instance;

    /** Room database shared by all callers in the current process. */
    private final AppDatabase appDatabase;

    /** On-device SQLite file name used by the imported reference schema. */
    private static final String DATABASE_NAME = "my_database";

    /** Creates the Room database and enables SQL logging only for debug builds. */
    private DatabaseClient(Context context) {
        androidx.room.RoomDatabase.Builder<AppDatabase> builder = Room.databaseBuilder(
                        context,
                        AppDatabase.class,
                        DATABASE_NAME
                )
                .fallbackToDestructiveMigration();
        if (BuildConfig.DEBUG) {
            builder.setQueryCallback(
                    (sql, args) -> Log.d("ROOM_SQL", "SQL: " + sql + " Args: " + args),
                    Executors.newSingleThreadExecutor()
            );
        }
        appDatabase = builder.build();
    }

    /**
     * Returns the process-local database client, creating it on first access.
     *
     * @param context any Android context; only its application context is retained by Room
     * @return the shared database client
     */
    public static synchronized DatabaseClient getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseClient(context);
        }
        return instance;
    }

    /** Returns the shared Room database; DAO calls must not run on the main thread. */
    public AppDatabase getAppDatabase() {
        return appDatabase;
    }
}
