package com.gdgravure.jobs.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.gdgravure.jobs.MainActivity
import com.gdgravure.jobs.data.JobRepository
import com.gdgravure.jobs.model.JobPost
import java.util.concurrent.TimeUnit

class BackgroundScrapeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME = "gd_gravure_periodic_scrape"
        private const val CHANNEL_ID = "gd_gravure_new_jobs"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackgroundScrapeWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val repo = JobRepository(applicationContext)
        val newJobs = repo.refreshAllLocalAndRemote()
        if (newJobs.isNotEmpty()) {
            sendNewJobsNotification(applicationContext, newJobs)
        }
        return Result.success()
    }

    private fun sendNewJobsNotification(context: Context, newJobs: List<JobPost>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "广东凹印新岗位提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(ch)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val first = newJobs.first()
        val summary = "发现 ${newJobs.size} 个新岗位：${first.city} ${first.title}（${first.salaryText}）"

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("粤印求职 · 新出广东凹印岗位")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                newJobs.take(4).joinToString("\n") { "• [${it.city}] ${it.title} | ${it.salaryText}" }
            ))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(10086, notif)
    }
}
