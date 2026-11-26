package ai.naru.app.comma

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-owned Room and writer: never close Room from ViewModel.onCleared(). */
object RestApplicationServices {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var databaseInstance: RestDatabase? = null
    @Volatile private var historyInstance: RestHistoryStore? = null
    @Volatile private var activeSessionInstance: ActiveRestSessionStore? = null

    fun history(context: Context): RestHistoryStore =
        historyInstance ?: synchronized(this) {
            historyInstance ?: RestHistoryStore(
                RoomRestHistoryRepository(database(context).restDao()),
                applicationScope,
            ).also { historyInstance = it }
        }

    fun activeSession(context: Context): ActiveRestSessionStore =
        activeSessionInstance ?: synchronized(this) {
            activeSessionInstance ?: ActiveRestSessionStore(database(context).restDao())
                .also { activeSessionInstance = it }
        }

    private fun database(context: Context): RestDatabase =
        databaseInstance ?: synchronized(this) {
            databaseInstance ?: Room.databaseBuilder(
                context.applicationContext,
                RestDatabase::class.java,
                "comma-rest.db",
            )
                .addMigrations(
                    RestDatabase.MIGRATION_1_2,
                    RestDatabase.MIGRATION_2_3,
                    RestDatabase.MIGRATION_3_4,
                    RestDatabase.MIGRATION_4_5,
                    RestDatabase.MIGRATION_5_6,
                )
                .build()
                .also { databaseInstance = it }
        }
}
