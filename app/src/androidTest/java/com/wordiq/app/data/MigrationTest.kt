package com.wordiq.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wordiq.app.data.local.WordIqDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WordIqDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesVersionOneToVersionTwo() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 2, true, WordIqDatabase.MIGRATION_1_2).close()
    }

    @Test
    fun migratesVersionTwoToVersionThree() {
        helper.createDatabase(TEST_DB_2_3, 2).close()
        helper.runMigrationsAndValidate(TEST_DB_2_3, 3, true, WordIqDatabase.MIGRATION_2_3).close()
    }

    @Test
    fun migratesVersionOneThroughVersionThree() {
        helper.createDatabase(TEST_DB_1_3, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB_1_3,
            3,
            true,
            WordIqDatabase.MIGRATION_1_2,
            WordIqDatabase.MIGRATION_2_3,
        ).close()
    }

    @Test
    fun migratesVersionThreeToVersionFour() {
        helper.createDatabase(TEST_DB_3_4, 3).close()
        helper.runMigrationsAndValidate(TEST_DB_3_4, 4, true, WordIqDatabase.MIGRATION_3_4).close()
    }

    @Test
    fun migratesVersionOneThroughVersionFour() {
        helper.createDatabase(TEST_DB_1_4, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB_1_4,
            4,
            true,
            WordIqDatabase.MIGRATION_1_2,
            WordIqDatabase.MIGRATION_2_3,
            WordIqDatabase.MIGRATION_3_4,
        ).close()
    }

    companion object {
        private const val TEST_DB = "migration-1-2"
        private const val TEST_DB_2_3 = "migration-2-3"
        private const val TEST_DB_1_3 = "migration-1-3"
        private const val TEST_DB_3_4 = "migration-3-4"
        private const val TEST_DB_1_4 = "migration-1-4"
    }
}
