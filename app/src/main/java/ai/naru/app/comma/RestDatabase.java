package ai.naru.app.comma;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {RestRecordEntity.class, ActiveRestSessionEntity.class}, version = 6, exportSchema = true)
public abstract class RestDatabase extends RoomDatabase {
    public abstract RestDao restDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE rest_records ADD COLUMN startBatteryPercent INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN endBatteryPercent INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN startIndoorCelsius REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN endIndoorCelsius REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN outdoorCelsius REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN targetClimateCelsius REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN screenBrightness REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN lightingBrightness REAL DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN sound TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN feedback INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN restoreSucceeded INTEGER DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE rest_records ADD COLUMN temperatureFeedback TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN feedbackSubmittedAt INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN source TEXT NOT NULL DEFAULT 'UNKNOWN'");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS active_rest_session (singletonKey TEXT NOT NULL, sessionId TEXT NOT NULL, owner TEXT NOT NULL, phase TEXT NOT NULL, startedAtWallMillis INTEGER NOT NULL, endAtWallMillis INTEGER NOT NULL, startedAtElapsedMillis INTEGER NOT NULL, deadlineElapsedMillis INTEGER NOT NULL, savedAtWallMillis INTEGER NOT NULL, savedAtElapsedMillis INTEGER NOT NULL, selectedMinutes INTEGER NOT NULL, plannedMinutes INTEGER NOT NULL, extensionMinutes INTEGER NOT NULL, approvedBrightness REAL NOT NULL, approvedLighting REAL NOT NULL, sound TEXT NOT NULL, approvedClimateCelsius REAL DEFAULT NULL, backupHvacPower INTEGER DEFAULT NULL, backupClimateCelsius REAL DEFAULT NULL, backupAmbientOn INTEGER DEFAULT NULL, backupAmbientBrightness INTEGER DEFAULT NULL, backupAmbientColor INTEGER DEFAULT NULL, restoreState TEXT NOT NULL, source TEXT NOT NULL, alarmIssued INTEGER NOT NULL, completionRecorded INTEGER NOT NULL, PRIMARY KEY(singletonKey))");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE rest_records ADD COLUMN hvacPowerRestore TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN climateTemperatureRestore TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN ambientLightRestore TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN screenBrightnessRestore TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN natureSoundRestore TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN alarmRestore TEXT NOT NULL DEFAULT 'UNKNOWN'");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE rest_records ADD COLUMN chargingStateAtStart TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN chargingStateAtEnd TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN chargePortConnectedDuringRest INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN chargingDetectedDuringRest INTEGER DEFAULT NULL");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN chargingObservationComplete INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE rest_records ADD COLUMN energyDataQuality TEXT NOT NULL DEFAULT 'UNKNOWN'");
        }
    };
}
