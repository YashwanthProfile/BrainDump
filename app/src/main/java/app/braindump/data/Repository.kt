package app.braindump.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Single entry point for reads and writes, shared by the UI and the notification receivers. */
class Repository private constructor(private val context: Context) {
    private val db = AppDatabase.get(context)
    val settings = SettingsStore(context)

    fun entries(date: String) = db.entries().forDate(date)
    fun threads() = db.threads().all()
    fun thread(id: String) = db.threads().get(id)
    fun threadNotes(id: String) = db.threads().notes(id)
    fun allThreadNotes() = db.threads().allNotes()
    fun searchThreads(q: String) = db.threads().search(q)

    /** Starts a thread with its first note: the reasoning. */
    suspend fun startThread(title: String, why: String, expectation: String, mood: String = "", major: Boolean = false, decidedAt: Long = System.currentTimeMillis()): String {
        val t = LifeThread(title = title.trim(), expectation = expectation.trim(), major = major, createdAt = decidedAt)
        db.threads().upsert(t)
        // Always keep the decision as the first note, even if you wrote no description yet.
        db.threads().upsert(ThreadNote(threadId = t.id, text = why.trim(), mood = mood, at = decidedAt))
        return t.id
    }
    suspend fun saveThread(t: LifeThread) = db.threads().upsert(t.copy(updatedAt = System.currentTimeMillis()))
    suspend fun addThreadNote(t: LifeThread, text: String, mood: String, at: Long = System.currentTimeMillis()) {
        db.threads().upsert(ThreadNote(threadId = t.id, text = text.trim(), mood = mood, at = at))
        saveThread(t)
    }
    suspend fun saveThreadNote(n: ThreadNote) = db.threads().upsert(n.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteThreadNote(n: ThreadNote) = db.threads().delete(n)
    suspend fun deleteThread(id: String) { db.threads().deleteNotesOf(id); db.threads().deleteThread(id) }

    fun searchEntries(q: String) = db.entries().search(q)
    fun searchDays(q: String) = db.days().search(q)
    suspend fun lastLogAt(): Long? = db.entries().lastCreatedAt()
    fun day(date: String) = db.days().get(date)
    fun dayPhotos(date: String) = db.attachments().forOwner("day", date)
    fun entryPhotos(date: String) = db.attachments().entryPhotosForDate(date)
    fun ideas() = db.ideas().all()
    fun recentSleep(limit: Int = 14) = db.sleep().recent(limit)
    fun openSleep() = db.sleep().open()
    fun entriesBetween(from: String, to: String) = db.entries().between(from, to)
    fun entryCounts(from: String, to: String) = db.entries().counts(from, to)
    fun daysBetween(from: String, to: String) = db.days().between(from, to)
    fun dayPhotosBetween(from: String, to: String) = db.attachments().dayPhotosBetween(from, to)
    fun meals(from: String, to: String = from) = db.meals().between(from, to)
    fun workouts() = db.gym().workouts()
    fun workout(id: String) = db.gym().workout(id)
    fun exercises(workoutId: String) = db.gym().exercises(workoutId)
    fun sets(workoutId: String) = db.gym().sets(workoutId)
    fun exerciseNames() = db.gym().exerciseNames()

    fun habits() = db.habits().active()
    fun archivedHabits() = db.habits().archived()
    fun habit(id: String) = db.habits().get(id)
    fun habitLogs(from: String, to: String) = db.habits().logsBetween(from, to)
    fun habitLogsFor(habitId: String) = db.habits().logsFor(habitId)

    suspend fun saveHabit(habit: Habit) {
        val pos = if (db.habits().getOnce(habit.id) == null) db.habits().count() else habit.position
        db.habits().upsert(habit.copy(name = habit.name.trim(), goal = habit.goal.trim(), unit = habit.unit.trim(), position = pos, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setArchived(habit: Habit, archived: Boolean) =
        db.habits().upsert(habit.copy(archived = archived, updatedAt = System.currentTimeMillis()))

    suspend fun deleteHabit(habitId: String) {
        db.habits().deleteLogsOf(habitId)
        db.habits().deleteHabit(habitId)
    }

    suspend fun logHabit(habitId: String, value: Double? = null, note: String = "", at: Long = System.currentTimeMillis()) =
        db.habits().upsert(HabitLog(habitId = habitId, date = Dates.dayOf(at), at = at, value = value, note = note.trim()))

    suspend fun saveHabitLog(log: HabitLog) =
        db.habits().upsert(log.copy(date = Dates.dayOf(log.at), updatedAt = System.currentTimeMillis()))

    suspend fun deleteHabitLog(log: HabitLog) = db.habits().delete(log)

    suspend fun addMeal(type: String, eatenAt: Long = System.currentTimeMillis(), note: String = "") =
        db.meals().upsert(Meal(date = Dates.dayOf(eatenAt), eatenAt = eatenAt, type = type, note = note.trim()))

    suspend fun saveMeal(meal: Meal) =
        db.meals().upsert(meal.copy(date = Dates.dayOf(meal.eatenAt), updatedAt = System.currentTimeMillis()))

    suspend fun deleteMeal(meal: Meal) = db.meals().delete(meal)

    /** Starts a workout now and returns its id so the caller can open it. */
    suspend fun addWorkout(title: String, startedAt: Long = System.currentTimeMillis()): String {
        val w = Workout(date = Dates.dayOf(startedAt), startedAt = startedAt, title = title.trim().ifEmpty { "Workout" })
        db.gym().upsert(w)
        return w.id
    }

    suspend fun saveWorkout(workout: Workout) = db.gym().upsert(workout.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteWorkout(workoutId: String) {
        db.gym().deleteSetsOfWorkout(workoutId)
        db.gym().deleteExercisesOf(workoutId)
        db.gym().deleteWorkout(workoutId)
    }

    suspend fun addExercise(workoutId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        db.gym().upsert(Exercise(workoutId = workoutId, name = clean, position = db.gym().exerciseCount(workoutId)))
    }

    suspend fun deleteExercise(exerciseId: String) {
        db.gym().deleteSetsOf(exerciseId)
        db.gym().deleteExercise(exerciseId)
    }

    suspend fun addSet(exerciseId: String, reps: Int, weightKg: Double) =
        db.gym().upsert(ExerciseSet(exerciseId = exerciseId, reps = reps, weightKg = weightKg, position = db.gym().setCount(exerciseId)))

    suspend fun deleteSet(set: ExerciseSet) = db.gym().delete(set)

    suspend fun addEntry(
        text: String,
        happenedAt: Long = System.currentTimeMillis(),
        coversFrom: Long? = null,
        kind: String = "log",
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        db.entries().upsert(
            Entry(date = Dates.dayOf(happenedAt), happenedAt = happenedAt, coversFrom = coversFrom, text = clean, kind = kind)
        )
    }

    suspend fun saveEntry(entry: Entry) =
        db.entries().upsert(entry.copy(date = Dates.dayOf(entry.happenedAt), updatedAt = System.currentTimeMillis()))

    suspend fun deleteEntry(entry: Entry) = db.entries().delete(entry)

    suspend fun saveDayNotes(date: String, notes: String) {
        val day = db.days().getOnce(date) ?: Day(date)
        db.days().upsert(day.copy(notes = notes, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveDayTitle(date: String, title: String) {
        val day = db.days().getOnce(date) ?: Day(date)
        db.days().upsert(day.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    /** A moment that is just a photo (with an optional caption added later). */
    suspend fun addPhotoMoment(date: String, at: Long, uri: Uri) {
        val entry = Entry(date = date, happenedAt = at, text = "", kind = "photo")
        db.entries().upsert(entry)
        addPhoto("entry", entry.id, uri)
    }

    suspend fun setReviewDone(date: String, done: Boolean) {
        val day = db.days().getOnce(date) ?: Day(date)
        db.days().upsert(day.copy(reviewDoneAt = if (done) System.currentTimeMillis() else null, updatedAt = System.currentTimeMillis()))
    }

    /** Copies a picked photo into app storage so it survives the original being deleted. */
    suspend fun addDayPhoto(date: String, uri: Uri) = addPhoto("day", date, uri)

    private suspend fun addPhoto(ownerType: String, ownerId: String, uri: Uri): String? {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = if (mime.endsWith("png")) "png" else "jpg"
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val name = "${newId()}.$ext"
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, name).outputStream().use { input.copyTo(it) }
        } ?: return null
        val a = Attachment(ownerType = ownerType, ownerId = ownerId, filePath = "media/$name", mime = mime)
        db.attachments().upsert(a)
        return a.id
    }

    suspend fun deletePhoto(attachment: Attachment) {
        File(context.filesDir, attachment.filePath).delete()
        db.attachments().delete(attachment)
    }

    suspend fun allAttachments() = db.attachments().allOnce()
    fun fileFor(attachment: Attachment) = File(context.filesDir, attachment.filePath)

    suspend fun addIdea(title: String, body: String, category: String) {
        if (title.isBlank() && body.isBlank()) return
        db.ideas().upsert(Idea(title = title.trim().ifEmpty { body.trim().take(60) }, body = body.trim(), category = category))
    }

    suspend fun saveIdea(idea: Idea) = db.ideas().upsert(idea.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteIdea(idea: Idea) = db.ideas().delete(idea)

    suspend fun goingToBed(at: Long = System.currentTimeMillis()) {
        if (db.sleep().openOnce() != null) return
        db.sleep().upsert(Sleep(bedAt = at))
    }

    suspend fun wokeUp(at: Long = System.currentTimeMillis()) {
        val open = db.sleep().openOnce() ?: return
        db.sleep().upsert(open.copy(wakeAt = at, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveSleep(sleep: Sleep) = db.sleep().upsert(sleep.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteSleep(sleep: Sleep) = db.sleep().delete(sleep)

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
