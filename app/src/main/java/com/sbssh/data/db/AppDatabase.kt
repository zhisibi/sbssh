package com.sbssh.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [VpsEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vpsDao(): VpsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                SQLiteDatabase.loadLibs(context)
                val hexPassphrase = passphrase.joinToString("") { "%02x".format(it) }
                val sqlCipherPassphrase = SQLiteDatabase.getBytes(hexPassphrase.toCharArray())
                val factory = SupportFactory(sqlCipherPassphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sbssh.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()

                // Force open database and create tables early so auth flow catches errors here,
                // instead of crashing later on first DAO access.
                instance.openHelper.writableDatabase

                INSTANCE = instance
                instance
            }
        }

        fun getInstance(): AppDatabase {
            return INSTANCE ?: throw IllegalStateException("Database not initialized. Call getInstance(context, passphrase) first.")
        }

        fun close() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
