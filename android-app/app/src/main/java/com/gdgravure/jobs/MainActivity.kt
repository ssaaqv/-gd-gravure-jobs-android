package com.gdgravure.jobs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.gdgravure.jobs.data.JobRepository
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost
import com.gdgravure.jobs.parser.LocalTextParser
import com.gdgravure.jobs.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShare(intent)

        val repo = JobRepository(applicationContext)

        setContent {
            GdGravureTheme {
                val scope = rememberCoroutineScope()
                val initialFeed = remember { repo.loadCachedRemoteJobs() }
                var remoteJobs by remember { mutableStateOf(initialFeed.jobs) }
                var updatedAt by remember { mutableStateOf(initialFeed.updatedAt) }
                var aiDigest by remember { mutableStateOf(initialFeed.aiDigest) }
                var manualJobs by remember { mutableStateOf(repo.getManualJobs()) }
                var statusMap by remember { mutableStateOf(repo.getStatusMap()) }
                var isLoading by remember { mutableStateOf(false) }
                var errorMsg by remember { mutableStateOf<String?>(null) }

                var selectedJob by remember { mutableStateOf<JobPost?>(null) }
                var showImportDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var feedUrlInput by remember { mutableStateOf(repo.getFeedUrl()) }

                val incomingShare by sharedTextState
                LaunchedEffect(incomingShare) {
                    if (!incomingShare.isNullOrBlank()) {
                        showImportDialog = true
                    }
                }

                val allJobs = remember(manualJobs, remoteJobs) { manualJobs + remoteJobs }

                JobListScreen(
                    jobs = allJobs,
                    updatedAt = updatedAt,
                    aiDigest = aiDigest,
                    isLoading = isLoading,
                    errorMsg = errorMsg,
                    statusMap = statusMap,
                    onRefresh = {
                        scope.launch {
                            isLoading = true
                            errorMsg = null
                            repo.fetchRemoteJobs()
                                .onSuccess { feed ->
                                    updatedAt = feed.updatedAt
                                    aiDigest = feed.aiDigest
                                    remoteJobs = feed.jobs
                                }
                                .onFailure { err ->
                                    errorMsg = err.message ?: "刷新失败"
                                }
                            isLoading = false
                        }
                    },
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
                        title = { Text("配置数据源 URL") },
                        text = {
                            OutlinedTextField(
                                value = feedUrlInput,
                                onValueChange = { feedUrlInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("jobs.json Raw 链接") },
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
                            TextButton(onClick = { showSettingsDialog = false }) { Text("取消") }
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
