package ai.naru.app.comma;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {RestRecordEntity.class}, version = 1, exportSchema = false)
public abstract class RestDatabase extends RoomDatabase {
    public abstract RestDao restDao();
}
