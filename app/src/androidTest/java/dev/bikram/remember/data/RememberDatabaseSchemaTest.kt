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
    fun exported_v1_schema_matches_room_model() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).close()

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            1,
            true,
        )
    }

    companion object {
        private const val TEST_DATABASE_NAME = "remember-schema-test.db"
    }
}
