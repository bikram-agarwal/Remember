package dev.bikram.remember.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RememberDatabaseSchemaTest {
    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RememberDatabase::class.java,
        )

    @Test
    fun migration_from_version_1_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_1_DATABASE_NAME, 1).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_1_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_1_2,
            RememberDatabase.MIGRATION_2_3,
            RememberDatabase.MIGRATION_3_4,
            RememberDatabase.MIGRATION_4_5,
        )
    }

    @Test
    fun migration_from_version_2_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_2_DATABASE_NAME, 2).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_2_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_2_3,
            RememberDatabase.MIGRATION_3_4,
            RememberDatabase.MIGRATION_4_5,
        )
    }

    @Test
    fun migration_from_version_3_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_3_DATABASE_NAME, 3).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_3_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_3_4,
            RememberDatabase.MIGRATION_4_5,
        )
    }

    @Test
    fun migration_from_version_4_reaches_current_schema() {
        migrationHelper.createDatabase(VERSION_4_DATABASE_NAME, 4).close()

        migrationHelper.runMigrationsAndValidate(
            VERSION_4_DATABASE_NAME,
            CURRENT_VERSION,
            true,
            RememberDatabase.MIGRATION_4_5,
        )
    }

    companion object {
        private const val CURRENT_VERSION = 5
        private const val VERSION_1_DATABASE_NAME = "remember-schema-v1-test.db"
        private const val VERSION_2_DATABASE_NAME = "remember-schema-v2-test.db"
        private const val VERSION_3_DATABASE_NAME = "remember-schema-v3-test.db"
        private const val VERSION_4_DATABASE_NAME = "remember-schema-v4-test.db"
    }
}
