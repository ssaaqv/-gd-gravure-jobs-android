package com.gdgravure.jobs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.gdgravure.jobs.data.JobRepository
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost
import com.gdgravure.jobs.parser.LocalTextParser
import com.gdgravure.jobs.ui.*
import com.gdgravure.jobs.worker.BackgroundScrapeWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedTextState = mutableStateOf<String?>(null)

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShare(intent)

        BackgroundScrapeWorker.schedulePeriodic(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val repo = JobRepository(applicationContext)

        setContent {
            GdGravureTheme {
                val scope = rememberCoroutineScope()
                val initialFeed = remember { repo.loadCachedFeed() }
                var remoteJobs by remember { mutableStateOf(initialFeed.jobs) }
                var updatedAt by remember { mutableStateOf(initialFeed.updatedAt) }
                var aiDigest by remember { mutableStateOf(initialFeed.aiDigest) }
                var manualJobs by remember { mutableStateOf(repo.getManualJobs()) }
                var statusMap by remember { mutableStateOf(repo.getStatusMap()) }
                var blacklistedPhones by remember { mutableStateOf(repo.getBlacklistedPhones()) }
                var isLoading by remember { mutableStateOf(false) }
                var errorMsg by remember { mutableStateOf<String?>(null) }

                var selectedJob by remember { mutableStateOf<JobPost?>(null) }
                var showImportDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var feedUrlInput by remember { mutableStateOf(repo.getFeedUrl()) }

                suspend fun runRefresh() {
                    isLoading = true
                    errorMsg = null
                    runCatching {
                        repo.refreshAllLocalAndRemote()
                    }.onSuccess {
                        val feed = repo.loadCachedFeed()
                        updatedAt = feed.updatedAt
                        aiDigest = feed.aiDigest
                        remoteJobs = feed.jobs
                        if (feed.jobs.isEmpty()) {
                            errorMsg = "本次未抓到新帖，请检查手机网络后重试，或点右下角“粘贴导入”。"
                        }
                    }.onFailure { err ->
                        errorMsg = err.message ?: "刷新失败"
                    }
                    isLoading = false
                }

                LaunchedEffect(Unit) {
                    if (remoteJobs.isEmpty()) {
                        runRefresh()
                    }
                }

                val incomingShare by sharedTextState
                LaunchedEffect(incomingShare) {
                    if (!incomingShare.isNullOrBlank()) {
                        showImportDialog = true
                    }
                }

                val allJobs = remember(manualJobs, remoteJobs, blacklistedPhones) {
                    (manualJobs + remoteJobs).filter { it.phone.isBlank() || it.phone !in blacklistedPhones }
                }

                JobListScreen(
                    jobs = allJobs,
                    updatedAt = updatedAt,
                    aiDigest = aiDigest,
                    isLoading = isLoading,
                    errorMsg = errorMsg,
                    statusMap = statusMap,
                    onRefresh = { scope.launch { runRefresh() } },
                    onOpenSettings = {
                        feedUrlInput = repo.getFeedUrl()
                        showSettingsDialog = true
                    },
                    onOpenImport = { showImportDialog = true },
                    onSelectJob = { selectedJob = it }
                )

                selectedJob?.let { job ->
                    JobDetailDialog(
                        job = job,
                        currentStatus = statusMap[job.id] ?: FollowStatus.UNREAD,
                        onStatusChange = { st ->
                            repo.setStatus(job.id, st)
                            statusMap = repo.getStatusMap()
                        },
                        onBlacklistPhone = { phone ->
                            repo.blacklistPhone(phone)
                            blacklistedPhones = repo.getBlacklistedPhones()
                        },
                        onDismiss = { selectedJob = null }
                    )
                }

                if (showImportDialog) {
                    QuickImportDialog(
                        initialText = incomingShare.orEmpty(),
                        onImport = { raw ->
                            val parsed = LocalTextParser.parsePastedText(raw)
                            repo.addManualJob(parsed)
                            manualJobs = repo.getManualJobs()
                            sharedTextState.value = null
                            showImportDialog = false
                        },
                        onDismiss = {
                            sharedTextState.value = null
                            showImportDialog = false
                        }
                    )
                }

                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        title = { Text("后台自动直抓已开启") },
                        text = {
                            OutlinedTextField(
                                value = feedUrlInput,
                                onValueChange = { feedUrlInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("可选：额外云端 jobs.json 链接（不填也能直抓）") },
                                singleLine = false
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                repo.saveFeedUrl(feedUrlInput)
                                showSettingsDialog = false
                            }) { Text("保存") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettingsDialog = false }) { Text("关闭") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                sharedTextState.value = shared
            }
        }
    }
}
