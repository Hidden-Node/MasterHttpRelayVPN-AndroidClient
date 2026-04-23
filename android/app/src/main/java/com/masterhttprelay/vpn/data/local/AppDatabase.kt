package com.masterhttprelay.vpn.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProfileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "masterhttprelay_vpn.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profiles_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        domains TEXT NOT NULL,
                        encryptionMethod INTEGER NOT NULL,
                        encryptionKey TEXT NOT NULL,
                        protocolType TEXT NOT NULL,
                        listenPort INTEGER NOT NULL,
                        packetDuplicationCount INTEGER NOT NULL,
                        setupPacketDuplicationCount INTEGER NOT NULL,
                        uploadCompression INTEGER NOT NULL,
                        downloadCompression INTEGER NOT NULL,
                        logLevel TEXT NOT NULL,
                        isSelected INTEGER NOT NULL,
                        advancedJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO profiles_new (
                        id, name, domains, encryptionMethod, encryptionKey, protocolType, listenPort,
                        packetDuplicationCount, setupPacketDuplicationCount, uploadCompression,
                        downloadCompression, logLevel, isSelected, advancedJson, createdAt
                    )
                    SELECT
                        id, name, domains, encryptionMethod, encryptionKey, protocolType, listenPort,
                        packetDuplicationCount, setupPacketDuplicationCount, uploadCompression,
                        downloadCompression, logLevel, isSelected, advancedJson, createdAt
                    FROM profiles
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE profiles")
                db.execSQL("ALTER TABLE profiles_new RENAME TO profiles")
            }
        }
    }
}
