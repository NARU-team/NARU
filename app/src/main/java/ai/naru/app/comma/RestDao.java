package ai.naru.app.comma;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface RestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(RestRecordEntity record);

    @Query("SELECT * FROM rest_records ORDER BY startedAt DESC LIMIT 50")
    List<RestRecordEntity> recent();
}
