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
}
