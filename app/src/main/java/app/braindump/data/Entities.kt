package app.braindump.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** One quick log line. `date` is the local day it belongs to (yyyy-MM-dd). */
@Entity(tableName = "entry", indices = [Index("date")])
data class Entry(
    @PrimaryKey val id: String = newId(),
    val date: String,
    val happenedAt: Long,
    val coversFrom: Long? = null,
    val text: String,
    val details: String = "",
    /** "log", "missed" or "note". */
    val kind: String = "log",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One row per day, holding the evening review. Entries are joined by date. */
@Entity(tableName = "day")
data class Day(
    @PrimaryKey val date: String,
    val notes: String = "",
    /** A short title for the day, shown in the month list. */
    @androidx.room.ColumnInfo(defaultValue = "") val title: String = "",
    val reviewDoneAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "attachment", indices = [Index("ownerType", "ownerId")])
data class Attachment(
    @PrimaryKey val id: String = newId(),
    /** "day", "entry" or "idea". */
    val ownerType: String,
    val ownerId: String,
    /** Path relative to the app's files directory. */
    val filePath: String,
    val mime: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "idea")
data class Idea(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val body: String = "",
    val category: String = IdeaCategory.OTHER,
    /** "new", "exploring", "parked" or "done". */
    val status: String = "new",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object IdeaCategory {
    const val RESEARCH = "research"
    const val QUESTION = "question"
    const val STARTUP = "startup"
    const val PERSONAL = "personal"
    const val OTHER = "other"
    val all = listOf(RESEARCH, QUESTION, STARTUP, PERSONAL, OTHER)
}

@Entity(tableName = "sleep")
data class Sleep(
    @PrimaryKey val id: String = newId(),
    val bedAt: Long,
    val wakeAt: Long? = null,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

object MealType {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"
    val main = listOf(BREAKFAST, LUNCH, DINNER)
    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
}

@Entity(tableName = "meal", indices = [Index("date")])
data class Meal(
    @PrimaryKey val id: String = newId(),
    val date: String,
    val eatenAt: Long,
    val type: String,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Gym hierarchy: a workout has exercises, an exercise has sets. */
@Entity(tableName = "workout", indices = [Index("date")])
data class Workout(
    @PrimaryKey val id: String = newId(),
    val date: String,
    val startedAt: Long,
    val title: String,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "exercise", indices = [Index("workoutId")])
data class Exercise(
    @PrimaryKey val id: String = newId(),
    val workoutId: String,
    val name: String,
    val position: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "exercise_set", indices = [Index("exerciseId")])
data class ExerciseSet(
    @PrimaryKey val id: String = newId(),
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double,
    val position: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class DayCount(val date: String, val n: Int)

object Measure {
    /** Just "did it". */
    const val CHECK = "check"
    /** A number each time, like kg lifted or km run. */
    const val NUMBER = "number"
}

/** Any habit you want to build or track. You define it; the app adapts to it. */
@Entity(tableName = "habit")
data class Habit(
    @PrimaryKey val id: String = newId(),
    val name: String,
    /** Why: what you want to improve. Shown at the top of the habit. */
    val goal: String = "",
    /** A key from the icon set in the UI. */
    val icon: String = "check",
    /** ARGB colour. */
    val color: Long = 0xFF5B7DB1,
    val measure: String = Measure.CHECK,
    /** Unit for NUMBER habits, such as "kg", "km" or "min". */
    val unit: String = "",
    /** How many times a week you are aiming for. */
    val weeklyTarget: Int = 3,
    val archived: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habit_log", indices = [Index("habitId"), Index("date")])
data class HabitLog(
    @PrimaryKey val id: String = newId(),
    val habitId: String,
    val date: String,
    val at: Long,
    val value: Double? = null,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A long-running topic you come back to: a decision, a question, a belief. */
@Entity(tableName = "life_thread")
data class LifeThread(
    @PrimaryKey val id: String = newId(),
    val title: String,
    /** What you expected to happen, written when you started. Optional. */
    val expectation: String = "",
    /** Days after the last note before the front page suggests revisiting. 0 = never. */
    val revisitDays: Int = 30,
    /** A big life decision (true) or a small one (false); used for sorting. */
    @androidx.room.ColumnInfo(defaultValue = "0") val major: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One dated note in a thread: the original reasoning, or a later check-in. */
@Entity(tableName = "thread_note", indices = [Index("threadId")])
data class ThreadNote(
    @PrimaryKey val id: String = newId(),
    val threadId: String,
    val at: Long = System.currentTimeMillis(),
    val text: String,
    /** "glad", "unsure", "regret", or "" for none. */
    val mood: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

object Mood {
    const val GLAD = "glad"
    const val UNSURE = "unsure"
    const val REGRET = "regret"
    val all = listOf(GLAD, UNSURE, REGRET)
}
