package com.examtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExamEntity::class, CustomTimelineEvent::class],
    version = 5,
    exportSchema = false
)
abstract class ExamDatabase : RoomDatabase() {

    abstract fun examDao(): ExamDao
    abstract fun customTimelineEventDao(): CustomTimelineEventDao

    companion object {
        @Volatile
        private var INSTANCE: ExamDatabase? = null

        fun getInstance(context: Context): ExamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExamDatabase::class.java,
                    "exam_tracker.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exams ADD COLUMN accountPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exams ADD COLUMN registeredPositionName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exams ADD COLUMN registeredPositionCode TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_timeline_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        examId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        icon TEXT NOT NULL DEFAULT '📌',
                        timestamp INTEGER NOT NULL,
                        calendarEventId INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (examId) REFERENCES exams(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_custom_timeline_events_examId ON custom_timeline_events (examId)")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exams ADD COLUMN isPaid INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_timeline_events ADD COLUMN eventType TEXT NOT NULL DEFAULT 'CUSTOM'")
            }
        }
    }
}
