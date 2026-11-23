package app.braindump.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entry WHERE date = :date ORDER BY happenedAt")
    fun forDate(date: String): Flow<List<Entry>>

    @Query("SELECT * FROM entry WHERE date BETWEEN :from AND :to ORDER BY happenedAt")
    fun between(from: String, to: String): Flow<List<Entry>>

    @Query("SELECT MAX(createdAt) FROM entry")
    suspend fun lastCreatedAt(): Long?

    @Query("SELECT * FROM entry WHERE text LIKE '%' || :q || '%' OR details LIKE '%' || :q || '%' ORDER BY happenedAt DESC LIMIT 300")
    fun search(q: String): Flow<List<Entry>>

    @Query("SELECT date AS date, COUNT(*) AS n FROM entry WHERE date BETWEEN :from AND :to GROUP BY date")
    fun counts(from: String, to: String): Flow<List<DayCount>>

    @Upsert
    suspend fun upsert(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)
}

@Dao
interface DayDao {
    @Query("SELECT * FROM day WHERE title LIKE '%' || :q || '%' OR notes LIKE '%' || :q || '%' ORDER BY date DESC LIMIT 300")
    fun search(q: String): Flow<List<Day>>

    @Query("SELECT * FROM day WHERE date = :date")
    fun get(date: String): Flow<Day?>

    @Query("SELECT * FROM day WHERE date = :date")
    suspend fun getOnce(date: String): Day?

    @Query("SELECT * FROM day WHERE date BETWEEN :from AND :to")
    fun between(from: String, to: String): Flow<List<Day>>

    @Upsert
    suspend fun upsert(day: Day)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt")
    fun forOwner(ownerType: String, ownerId: String): Flow<List<Attachment>>

    @Query("SELECT a.* FROM attachment a JOIN entry e ON a.ownerType = 'entry' AND a.ownerId = e.id WHERE e.date = :date ORDER BY a.createdAt")
    fun entryPhotosForDate(date: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment WHERE ownerType = 'day' AND ownerId BETWEEN :from AND :to ORDER BY createdAt")
    fun dayPhotosBetween(from: String, to: String): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment ORDER BY createdAt")
    suspend fun allOnce(): List<Attachment>

    @Upsert
    suspend fun upsert(attachment: Attachment)

    @Delete
    suspend fun delete(attachment: Attachment)
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM idea ORDER BY createdAt DESC")
    fun all(): Flow<List<Idea>>

    @Upsert
    suspend fun upsert(idea: Idea)

    @Delete
    suspend fun delete(idea: Idea)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep ORDER BY bedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Sleep>>

    @Query("SELECT * FROM sleep WHERE wakeAt IS NULL ORDER BY bedAt DESC LIMIT 1")
    fun open(): Flow<Sleep?>

    @Query("SELECT * FROM sleep WHERE wakeAt IS NULL ORDER BY bedAt DESC LIMIT 1")
    suspend fun openOnce(): Sleep?

    @Upsert
    suspend fun upsert(sleep: Sleep)

    @Delete
    suspend fun delete(sleep: Sleep)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal WHERE date BETWEEN :from AND :to ORDER BY eatenAt")
    fun between(from: String, to: String): Flow<List<Meal>>

    @Upsert
    suspend fun upsert(meal: Meal)

    @Delete
    suspend fun delete(meal: Meal)
}

@Dao
interface GymDao {
    @Query("SELECT * FROM workout ORDER BY startedAt DESC")
    fun workouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workout WHERE id = :id")
    fun workout(id: String): Flow<Workout?>

    @Query("SELECT * FROM exercise WHERE workoutId = :workoutId ORDER BY position")
    fun exercises(workoutId: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise_set WHERE exerciseId IN (SELECT id FROM exercise WHERE workoutId = :workoutId) ORDER BY position")
    fun sets(workoutId: String): Flow<List<ExerciseSet>>

    @Query("SELECT name FROM exercise GROUP BY name ORDER BY MAX(updatedAt) DESC LIMIT 40")
    fun exerciseNames(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM exercise WHERE workoutId = :workoutId")
    suspend fun exerciseCount(workoutId: String): Int

    @Query("SELECT COUNT(*) FROM exercise_set WHERE exerciseId = :exerciseId")
    suspend fun setCount(exerciseId: String): Int

    @Upsert
    suspend fun upsert(workout: Workout)

    @Upsert
    suspend fun upsert(exercise: Exercise)

    @Upsert
    suspend fun upsert(set: ExerciseSet)

    @Delete
    suspend fun delete(set: ExerciseSet)

    @Query("DELETE FROM exercise_set WHERE exerciseId = :exerciseId")
    suspend fun deleteSetsOf(exerciseId: String)

    @Query("DELETE FROM exercise WHERE id = :exerciseId")
    suspend fun deleteExercise(exerciseId: String)

    @Query("DELETE FROM exercise_set WHERE exerciseId IN (SELECT id FROM exercise WHERE workoutId = :workoutId)")
    suspend fun deleteSetsOfWorkout(workoutId: String)

    @Query("DELETE FROM exercise WHERE workoutId = :workoutId")
    suspend fun deleteExercisesOf(workoutId: String)

    @Query("DELETE FROM workout WHERE id = :workoutId")
    suspend fun deleteWorkout(workoutId: String)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit WHERE archived = 0 ORDER BY position, createdAt")
    fun active(): Flow<List<Habit>>

    @Query("SELECT * FROM habit WHERE archived = 1 ORDER BY updatedAt DESC")
    fun archived(): Flow<List<Habit>>

    @Query("SELECT * FROM habit WHERE id = :id")
    fun get(id: String): Flow<Habit?>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun getOnce(id: String): Habit?

    @Query("SELECT COUNT(*) FROM habit")
    suspend fun count(): Int

    @Query("SELECT * FROM habit_log WHERE date BETWEEN :from AND :to ORDER BY at")
    fun logsBetween(from: String, to: String): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_log WHERE habitId = :habitId ORDER BY at DESC")
    fun logsFor(habitId: String): Flow<List<HabitLog>>

    @Upsert
    suspend fun upsert(habit: Habit)

    @Upsert
    suspend fun upsert(log: HabitLog)

    @Delete
    suspend fun delete(log: HabitLog)

    @Query("DELETE FROM habit_log WHERE habitId = :habitId")
    suspend fun deleteLogsOf(habitId: String)

    @Query("DELETE FROM habit WHERE id = :habitId")
    suspend fun deleteHabit(habitId: String)
}

/** Version 3 adds custom habits. */
@Dao
interface ThreadDao {
    @Query("SELECT * FROM life_thread ORDER BY updatedAt DESC")
    fun all(): Flow<List<LifeThread>>

    @Query("SELECT * FROM life_thread WHERE id = :id")
    fun get(id: String): Flow<LifeThread?>

    @Query("SELECT * FROM thread_note WHERE threadId = :threadId ORDER BY at")
    fun notes(threadId: String): Flow<List<ThreadNote>>

    @Query("SELECT * FROM thread_note ORDER BY at")
    fun allNotes(): Flow<List<ThreadNote>>

    @Query("SELECT * FROM life_thread WHERE title LIKE '%' || :q || '%' OR expectation LIKE '%' || :q || '%' OR id IN (SELECT threadId FROM thread_note WHERE text LIKE '%' || :q || '%')")
    fun search(q: String): Flow<List<LifeThread>>

    @Upsert suspend fun upsert(t: LifeThread)
    @Upsert suspend fun upsert(n: ThreadNote)
    @Delete suspend fun delete(n: ThreadNote)
    @Query("DELETE FROM thread_note WHERE threadId = :id") suspend fun deleteNotesOf(id: String)
    @Query("DELETE FROM life_thread WHERE id = :id") suspend fun deleteThread(id: String)
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE life_thread ADD COLUMN major INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `life_thread` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `expectation` TEXT NOT NULL, `revisitDays` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `thread_note` (`id` TEXT NOT NULL, `threadId` TEXT NOT NULL, `at` INTEGER NOT NULL, `text` TEXT NOT NULL, `mood` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_note_threadId` ON `thread_note` (`threadId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE day ADD COLUMN title TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `habit` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `goal` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `measure` TEXT NOT NULL, `unit` TEXT NOT NULL, `weeklyTarget` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `habit_log` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `date` TEXT NOT NULL, `at` INTEGER NOT NULL, `value` REAL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_log_habitId` ON `habit_log` (`habitId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_log_date` ON `habit_log` (`date`)")
    }
}

/** Version 2 adds meals and the gym tables. Existing journal data is untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `meal` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `eatenAt` INTEGER NOT NULL, `type` TEXT NOT NULL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_date` ON `meal` (`date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_date` ON `workout` (`date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `exercise` (`id` TEXT NOT NULL, `workoutId` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_workoutId` ON `exercise` (`workoutId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_set` (`id` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_set_exerciseId` ON `exercise_set` (`exerciseId`)")
    }
}

@Database(
    entities = [
        Entry::class, Day::class, Attachment::class, Idea::class, Sleep::class,
        Meal::class, Workout::class, Exercise::class, ExerciseSet::class, Habit::class, HabitLog::class,
        LifeThread::class, ThreadNote::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun days(): DayDao
    abstract fun attachments(): AttachmentDao
    abstract fun ideas(): IdeaDao
    abstract fun sleep(): SleepDao
    abstract fun meals(): MealDao
    abstract fun gym(): GymDao
    abstract fun habits(): HabitDao
    abstract fun threads(): ThreadDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "braindump.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
