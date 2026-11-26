package ai.naru.app.comma

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestDatabaseMigrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var openDatabase: RestDatabase? = null
    private var databaseName: String? = null

    @After
    fun tearDown() {
        openDatabase?.close()
        databaseName?.let(context::deleteDatabase)
    }

    @Test fun migrateV1ToV6() = verifyMigrationFrom(1)
    @Test fun migrateV2ToV6() = verifyMigrationFrom(2)
    @Test fun migrateV3ToV6() = verifyMigrationFrom(3)
    @Test fun migrateV4ToV6() = verifyMigrationFrom(4)
    @Test fun migrateV5ToV6() = verifyMigrationFrom(5)

    private fun verifyMigrationFrom(startVersion: Int) {
        val name = "comma-migration-$startVersion.db"
        databaseName = name
        context.deleteDatabase(name)
        createHistoricalFixture(name, startVersion)

        val database = Room.databaseBuilder(context, RestDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        openDatabase = database

        val record = database.restDao().recent().single()
        assertEquals("fixture-$startVersion", record.id)
        if (startVersion >= 2) assertEquals(4, record.feedback) else assertNull(record.feedback)
        if (startVersion >= 3) {
            assertEquals("COMFORTABLE", record.temperatureFeedback)
        } else {
            assertNull(record.temperatureFeedback)
        }
        assertEquals("UNKNOWN", record.chargingStateAtStart)
        assertEquals("UNKNOWN", record.chargingStateAtEnd)
        assertEquals("UNKNOWN", record.energyDataQuality)
        assertEquals(false, record.chargingObservationComplete)

        if (startVersion >= 4) {
            val active = database.restDao().activeSession
            assertNotNull(active)
            assertEquals("active-$startVersion", active?.sessionId)
            assertEquals(24.5f, active?.backupClimateCelsius)
            assertEquals(35, active?.backupAmbientBrightness)
        } else {
            assertNull(database.restDao().activeSession)
        }

        val duplicate = RestRecordEntity().apply {
            id = record.id
            startedAt = 999L
            endedAt = 1_000L
            plannedMinutes = 1
            elapsedMillis = 1L
            reason = "DUPLICATE"
        }
        assertEquals(-1L, database.restDao().save(duplicate))
        val preserved = database.restDao().recent().single()
        if (startVersion >= 2) assertEquals(4, preserved.feedback) else assertNull(preserved.feedback)
        if (startVersion >= 3) {
            assertEquals("COMFORTABLE", preserved.temperatureFeedback)
        } else {
            assertNull(preserved.temperatureFeedback)
        }
    }

    private fun createHistoricalFixture(name: String, startVersion: Int) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS rest_records (" +
                            "id TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, " +
                            "plannedMinutes INTEGER NOT NULL, elapsedMillis INTEGER NOT NULL, " +
                            "reason TEXT NOT NULL, PRIMARY KEY(id))",
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO rest_records " +
                "(id, startedAt, endedAt, plannedMinutes, elapsedMillis, reason) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("fixture-$startVersion", 100L, 200L, 30, 100L, "COMPLETED"),
        )
        ALL_MIGRATIONS.filter { it.endVersion <= startVersion }.forEach { migration ->
            migration.migrate(db)
        }
        if (startVersion >= 2) {
            db.execSQL(
                "UPDATE rest_records SET feedback = 4, targetClimateCelsius = 23.5 " +
                    "WHERE id = ?",
                arrayOf("fixture-$startVersion"),
            )
        }
        if (startVersion >= 3) {
            db.execSQL(
                "UPDATE rest_records SET temperatureFeedback = 'COMFORTABLE', " +
                    "feedbackSubmittedAt = 300, source = 'UNKNOWN' WHERE id = ?",
                arrayOf("fixture-$startVersion"),
            )
        }
        if (startVersion >= 4) insertActiveSession(db, startVersion)
        db.version = startVersion
        helper.close()
    }

    private fun insertActiveSession(db: SupportSQLiteDatabase, version: Int) {
        db.execSQL(
            "INSERT INTO active_rest_session (" +
                "singletonKey, sessionId, owner, phase, startedAtWallMillis, endAtWallMillis, " +
                "startedAtElapsedMillis, deadlineElapsedMillis, savedAtWallMillis, savedAtElapsedMillis, " +
                "selectedMinutes, plannedMinutes, extensionMinutes, approvedBrightness, approvedLighting, " +
                "sound, approvedClimateCelsius, backupHvacPower, backupClimateCelsius, backupAmbientOn, " +
                "backupAmbientBrightness, backupAmbientColor, restoreState, source, alarmIssued, completionRecorded" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                "comma", "active-$version", "COMMA", "RESTING",
                100L, 1_900L, 10L, 1_810L, 120L, 30L,
                30, 30, 0, 0.4f, 0.2f, "RAIN", 23.5f,
                1, 24.5f, 1, 35, 0xFFFFFF, "PENDING", "UNKNOWN", 0, 0,
            ),
        )
    }

    private companion object {
        val ALL_MIGRATIONS = arrayOf(
            RestDatabase.MIGRATION_1_2,
            RestDatabase.MIGRATION_2_3,
            RestDatabase.MIGRATION_3_4,
            RestDatabase.MIGRATION_4_5,
            RestDatabase.MIGRATION_5_6,
        )
    }
}
