package com.wordiq.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CollectionEntity::class,
        FlashcardSetEntity::class,
        VocabularyConceptEntity::class,
        WordFormEntity::class,
        ExampleSentenceEntity::class,
        SetMembershipEntity::class,
        LearningStateEntity::class,
        ReviewLogEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class WordIqDatabase : RoomDatabase() {
    abstract fun wordIqDao(): WordIqDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `review_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conceptId` INTEGER NOT NULL,
                        `skill` TEXT NOT NULL,
                        `exerciseType` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `internalRating` TEXT NOT NULL,
                        `responseLatencyMs` INTEGER NOT NULL,
                        `hintsUsed` INTEGER NOT NULL,
                        `revealed` INTEGER NOT NULL,
                        `scheduled` INTEGER NOT NULL,
                        `reviewedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`conceptId`) REFERENCES `vocabulary_concepts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_conceptId` ON `review_logs` (`conceptId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_reviewedAt` ON `review_logs` (`reviewedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_scheduled` ON `review_logs` (`scheduled`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `review_logs` ADD COLUMN `audioReplayCount` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `word_forms` ADD COLUMN `persianMeaning` TEXT")
                db.execSQL("ALTER TABLE `word_forms` ADD COLUMN `persianNote` TEXT")
                db.execSQL(
                    "ALTER TABLE `review_logs` ADD COLUMN `lemmaRemembered` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun create(context: Context): WordIqDatabase =
            Room.databaseBuilder(context, WordIqDatabase::class.java, "wordiq.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
