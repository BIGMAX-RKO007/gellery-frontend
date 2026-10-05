package com.example.voxmate.persona.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * VoxMate 人物包专用 Room 数据库。
 *
 * 数据库只保存索引和当前选择，人物 JSON、头像和声音资源仍位于 assets 或应用私有目录。
 */
@Database(
  entities = [PersonaPackageEntity::class, ActivePersonaEntity::class],
  version = 2,
  exportSchema = true,
)
abstract class PersonaDatabase : RoomDatabase() {
  /** 返回人物索引和当前选择 DAO。 */
  abstract fun personaDao(): PersonaDao

  /** 负责进程内单例创建，避免多个 Room 实例同时打开同一数据库。 */
  companion object {
    /** 只持有数据库进程单例，不保存 Activity Context。 */
    @Volatile private var instance: PersonaDatabase? = null

    /**
     * 返回应用级人物数据库。
     *
     * @param context 任意 Android Context；内部只使用 Application Context
     * @return 进程内共享数据库实例
     */
    fun getInstance(context: Context): PersonaDatabase =
      instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                PersonaDatabase::class.java,
                DATABASE_NAME,
              )
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { database -> instance = database }
        }

    /** 人物数据库文件名，与密码业务参考库完全隔离。 */
    private const val DATABASE_NAME = "voxmate_personas.db"

    /** 升级旧 master 数据库，保留人物索引，但不把历史自动选择当作用户授权。 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
      /**
       * 增加显式选择标识，历史行置为 false；不删除包索引或人物资源。
       * @param db Room 提供的升级事务连接，在后台数据库线程执行
       */
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE active_persona ADD COLUMN explicitlySelected INTEGER NOT NULL DEFAULT 0")
      }
    }
  }
}
