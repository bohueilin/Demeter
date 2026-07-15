package com.demeter.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        WindowEvidenceEntity::class,
        ReminderRuleEntity::class,
        ScheduledReminderEntity::class,
        ReminderEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DemeterDb : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        fun build(context: Context): DemeterDb =
            Room.databaseBuilder(context, DemeterDb::class.java, "demeter.db").build()
    }
}
