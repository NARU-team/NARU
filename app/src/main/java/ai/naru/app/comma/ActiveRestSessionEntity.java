package ai.naru.app.comma;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "active_rest_session")
public class ActiveRestSessionEntity {
    @PrimaryKey @NonNull public String singletonKey = "comma";
    @NonNull public String sessionId = "";
    @NonNull public String owner = "COMMA";
    @NonNull public String phase = "RESTING";
    public long startedAtWallMillis;
    public long endAtWallMillis;
    public long startedAtElapsedMillis;
    public long deadlineElapsedMillis;
    public long savedAtWallMillis;
    public long savedAtElapsedMillis;
    public int selectedMinutes;
    public int plannedMinutes;
    public int extensionMinutes;
    public float approvedBrightness;
    public float approvedLighting;
    @NonNull public String sound = "SILENT";
    public Float approvedClimateCelsius;
    public Boolean backupHvacPower;
    public Float backupClimateCelsius;
    public Boolean backupAmbientOn;
    public Integer backupAmbientBrightness;
    public Integer backupAmbientColor;
    @NonNull public String restoreState = "PENDING";
    @NonNull public String source = "UNKNOWN";
    public boolean alarmIssued;
    public boolean completionRecorded;
}
