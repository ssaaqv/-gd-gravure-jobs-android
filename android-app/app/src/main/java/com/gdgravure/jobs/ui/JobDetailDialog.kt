package com.gdgravure.jobs.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost

@Composable
fun JobDetailDialog(
    job: JobPost,
    currentStatus: FollowStatus,
    onStatusChange: (FollowStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("企业：${job.company}  |  城市：${job.city}", style = MaterialTheme.typography.bodyMedium)
                Text("岗位：${job.role}  |  薪资：${job.salaryText}（${job.salaryType}）", fontWeight = FontWeight.SemiBold, color = AccentOrange)
                Text("排班：${job.shift}  |  月休/天数：${job.workDays}", style = MaterialTheme.typography.bodyMedium)

                if (job.machineTags.isNotEmpty()) {
                    Text("机型：${job.machineTags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
                if (job.materialTags.isNotEmpty()) {
                    Text("膜材/袋型：${job.materialTags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
                if (job.benefitTags.isNotEmpty()) {
                    Text("福利：${job.benefitTags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
                if (job.repeatCount >= 3) {
                    Text(
                        "避坑提醒：该电话近90天已发布 ${job.repeatCount} 次招聘，建议问清车间环境与流失原因。",
                        color = WarnText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()
                Text("原文详情：", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text(job.rawText.ifBlank { "无更多正文" }, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
                Text("标记跟进状态：", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FollowStatus.entries.forEach { st ->
                        FilterChip(
                            selected = currentStatus == st,
                            onClick = { onStatusChange(st) },
                            label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.phone.isNotBlank()) {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.phone}"))
                        context.startActivity(intent)
                    }) {
                        Text("拨打 ${job.phone.takeLast(4)}")
                    }
                }
                if (job.sourceUrl.isNotBlank()) {
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(job.sourceUrl))
                        context.startActivity(intent)
                    }) {
                        Text("看原帖")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
