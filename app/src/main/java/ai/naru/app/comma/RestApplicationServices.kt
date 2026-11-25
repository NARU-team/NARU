package ai.naru.app.comma

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-owned Room and writer: never close Room from ViewModel.onCleared(). */
object RestApplicationServices {
    @Volatile private var instance: RestHistoryStore? = null

    fun history(context: Context): RestHistoryStore =
        instance ?: synchronized(this) {
            instance ?: run {
                val database = Room.databaseBuilder(context.applicationContext,
                    RestDatabase::class.java, "comma-rest.db").build()
                RestHistoryStore(RoomRestHistoryRepository(database.restDao()),
                    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
            }.also { instance = it }
        }
}
