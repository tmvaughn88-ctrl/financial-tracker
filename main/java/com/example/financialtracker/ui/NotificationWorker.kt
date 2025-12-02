// NotificationWorker.kt
package com.example.financialtracker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.Date

@RequiresApi(Build.VERSION_CODES.O)
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME_MORNING = "FinancialTrackerNotificationWorker_Morning"
        private const val WORK_NAME_AFTERNOON = "FinancialTrackerNotificationWorker_Afternoon"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workManager = WorkManager.getInstance(context)

            // --- Worker 1: Morning Check (for TODAY's bills) ---
            val now = Calendar.getInstance()
            val morningFireTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0) // Target 12 AM (midnight)
                set(Calendar.MINUTE, 15)     // 12:15 AM
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val morningDelay = morningFireTime.timeInMillis - now.timeInMillis

            val morningRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(morningDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME_MORNING)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_MORNING,
                ExistingPeriodicWorkPolicy.UPDATE,
                morningRequest
            )

            // --- Worker 2: Afternoon Check (for TOMORROW's bills) ---
            val afternoonFireTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 17) // Target 5 PM (17:00)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val afternoonDelay = afternoonFireTime.timeInMillis - now.timeInMillis

            val afternoonRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(afternoonDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME_AFTERNOON)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_AFTERNOON,
                ExistingPeriodicWorkPolicy.UPDATE,
                afternoonRequest
            )

            Log.d("NotificationWorker", "Workers scheduled.")
        }

        fun scheduleOneTimeCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setConstraints(constraints)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }
    }

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "Worker starting: ${this.id}, Tag: ${tags.joinToString()}")

        val isMorningCheck = tags.contains(WORK_NAME_MORNING)
        val isAfternoonCheck = tags.contains(WORK_NAME_AFTERNOON)

        // 1. Determine Target Date Range for Standard Bills
        val targetCalendar = Calendar.getInstance()
        val dayOffset: Int
        val notificationTitle: String

        when {
            isMorningCheck -> {
                dayOffset = 0
                notificationTitle = "Bill Due TODAY Reminder"
            }
            isAfternoonCheck -> {
                dayOffset = 1
                notificationTitle = "Bill Due TOMORROW Reminder"
            }
            else -> {
                dayOffset = 0
                notificationTitle = "Upcoming Bill Reminder"
            }
        }

        targetCalendar.add(Calendar.DAY_OF_YEAR, dayOffset)

        val startOfTargetDay = getStartOfDay(targetCalendar.time)
        val endOfTargetDay = getEndOfDay(targetCalendar.time)

        try {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
            }
            val auth = Firebase.auth
            val db = Firebase.firestore
            val currentUser = auth.currentUser ?: return Result.success()

            var notificationId = 1

            // 2. Find all trackers
            val trackers = db.collection("trackers")
                .whereArrayContains("members", currentUser.uid)
                .get().await()
                .toObjects(Tracker::class.java)

            if (trackers.isEmpty()) return Result.success()

            for (tracker in trackers) {
                // --- A. STANDARD CHECK (Existing Logic) ---
                val standardItems = db.collection("trackers").document(tracker.id)
                    .collection("recurringItems")
                    .whereGreaterThanOrEqualTo("nextDate", startOfTargetDay)
                    .whereLessThanOrEqualTo("nextDate", endOfTargetDay)
                    .get().await()
                    .toObjects(RecurringItem::class.java)

                for (item in standardItems) {
                    val content = "${item.description} ($${String.format("%,.2f", item.amount)}) is due."
                    NotificationHelper.showNotification(
                        applicationContext,
                        notificationTitle,
                        content,
                        notificationId++,
                        startOfTargetDay.time
                    )
                }

                // --- B. FLUCTUATING CHECK (New Logic - Morning Only) ---
                // We only perform the "14 days out" check during the morning run
                if (isMorningCheck) {
                    val futureCalendar = Calendar.getInstance()
                    futureCalendar.add(Calendar.DAY_OF_YEAR, 14) // Look exactly 14 days ahead

                    val startOfFutureDay = getStartOfDay(futureCalendar.time)
                    val endOfFutureDay = getEndOfDay(futureCalendar.time)

                    val fluctuatingItems = db.collection("trackers").document(tracker.id)
                        .collection("recurringItems")
                        .whereEqualTo("isFluctuating", true)
                        .whereGreaterThanOrEqualTo("nextDate", startOfFutureDay)
                        .whereLessThanOrEqualTo("nextDate", endOfFutureDay)
                        .get().await()
                        .toObjects(RecurringItem::class.java)

                    for (item in fluctuatingItems) {
                        NotificationHelper.showNotification(
                            applicationContext,
                            "Update Bill Amount",
                            "Your ${item.description} is due in 14 days. Tap to scan and update the amount.",
                            notificationId++,
                            startOfFutureDay.time
                        )
                    }
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e("NotificationWorker", "Worker failed", e)
            return Result.failure()
        }
    }

    private fun getStartOfDay(date: Date): Date {
        val cal = Calendar.getInstance().apply { time = date }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun getEndOfDay(date: Date): Date {
        val cal = Calendar.getInstance().apply { time = date }
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }
}