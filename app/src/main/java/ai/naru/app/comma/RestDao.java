package ai.naru.app.comma;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface RestDao {
    // 종료 기록 재시도는 기존 피드백을 덮어쓰지 않는다.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long save(RestRecordEntity record);

    @Query("SELECT * FROM rest_records ORDER BY startedAt DESC LIMIT 50")
    List<RestRecordEntity> recent();

    @Query("UPDATE rest_records SET feedback = :satisfaction, temperatureFeedback = :temperatureFeedback, feedbackSubmittedAt = :submittedAt WHERE id = :sessionId AND feedbackSubmittedAt IS NULL")
    int saveFeedbackOnce(String sessionId, int satisfaction, String temperatureFeedback, long submittedAt);

    @Query("UPDATE rest_records SET restoreSucceeded = :succeeded, hvacPowerRestore = :hvacPower, climateTemperatureRestore = :temperature, ambientLightRestore = :ambient, screenBrightnessRestore = :screen, natureSoundRestore = :sound, alarmRestore = :alarm WHERE id = :sessionId")
    int updateRestoration(String sessionId, Boolean succeeded, String hvacPower, String temperature,
        String ambient, String screen, String sound, String alarm);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveActiveSession(ActiveRestSessionEntity session);

    @Query("SELECT * FROM active_rest_session WHERE singletonKey = 'comma' LIMIT 1")
    ActiveRestSessionEntity getActiveSession();

    @Query("DELETE FROM active_rest_session WHERE singletonKey = 'comma'")
    int deleteActiveSession();

    @Query("UPDATE active_rest_session SET alarmIssued = 1 WHERE singletonKey = 'comma' AND alarmIssued = 0")
    int markAlarmIssuedOnce();

    @Query("UPDATE active_rest_session SET completionRecorded = 1 WHERE singletonKey = 'comma' AND completionRecorded = 0")
    int markCompletionRecordedOnce();
}
