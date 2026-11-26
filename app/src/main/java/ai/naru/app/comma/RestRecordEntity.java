package ai.naru.app.comma;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "rest_records")
public class RestRecordEntity {
    @PrimaryKey @NonNull public String id = "";
    public long startedAt;
    public long endedAt;
    public int plannedMinutes;
    public long elapsedMillis;
    @NonNull public String reason = "";
    public Integer startBatteryPercent;
    public Integer endBatteryPercent;
    public Float startIndoorCelsius;
    public Float endIndoorCelsius;
    public Float outdoorCelsius;
    public Float targetClimateCelsius;
    public Float screenBrightness;
    public Float lightingBrightness;
    public String sound;
    public Integer feedback;
    public String temperatureFeedback;
    public Long feedbackSubmittedAt;
    @NonNull public String source = "UNKNOWN";
    public Boolean restoreSucceeded;
    @NonNull public String hvacPowerRestore = "UNKNOWN";
    @NonNull public String climateTemperatureRestore = "UNKNOWN";
    @NonNull public String ambientLightRestore = "UNKNOWN";
    @NonNull public String screenBrightnessRestore = "UNKNOWN";
    @NonNull public String natureSoundRestore = "UNKNOWN";
    @NonNull public String alarmRestore = "UNKNOWN";
    @NonNull public String chargingStateAtStart = "UNKNOWN";
    @NonNull public String chargingStateAtEnd = "UNKNOWN";
    public Boolean chargePortConnectedDuringRest;
    public Boolean chargingDetectedDuringRest;
    public boolean chargingObservationComplete;
    @NonNull public String energyDataQuality = "UNKNOWN";
}
