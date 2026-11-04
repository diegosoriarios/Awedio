package com.diego.awedio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.diego.awedio.R
import com.diego.awedio.data.TranscriptionEntity
import com.diego.awedio.data.TranscriptionStatus
import com.diego.awedio.model.TranscriptionLanguage
import com.diego.awedio.model.WhisperModelDef
import com.diego.awedio.model.WhisperModels
import com.diego.awedio.util.AppLogger
import com.diego.awedio.util.LogEntry
import com.diego.awedio.util.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val transcriptions by viewModel.transcriptions.collectAsState()
    val selectedTranscription by viewModel.selectedTranscription.collectAsState()
    val isModelDownloaded by viewModel.isModelDownloaded.collectAsState()
    val isDownloadingModel by viewModel.isDownloadingModel.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val modelDownloadStatus by viewModel.modelDownloadStatus.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val showModelMissingDialog by viewModel.showModelMissingDialog.collectAsState()
    val transcriptionLanguage by viewModel.transcriptionLanguage.collectAsState()

    var showLogSheet by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.msg_copied)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    if (transcriptions.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.clear_history_cd)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Model Status Banner / Downloader
            ModelStatusCard(
                selectedModel = selectedModel,
                isModelDownloaded = isModelDownloaded,
                isDownloading = isDownloadingModel,
                progress = downloadProgress,
                languageLabel = stringResource(transcriptionLanguage.labelRes),
                onClick = { showModelPicker = true },
                onDownloadClick = { viewModel.downloadModel() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status Message / Active Processing Banner
            statusMessage?.let { msg ->
                val statusText = stringResource(msg.resId, *msg.args.toTypedArray())
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTranscribing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            } else {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!isTranscribing) {
                            IconButton(onClick = { viewModel.clearStatusMessage() }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    }
                }
            }

            // History List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.history_count, transcriptions.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // History List or Empty State
            if (transcriptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.height(48.dp).width(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_history_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.empty_history_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = transcriptions, key = { item -> item.id }) { item ->
                        TranscriptionCard(
                            item = item,
                            onClick = { viewModel.selectTranscription(item) },
                            onCopyClick = {
                                copyToClipboard(context, item.transcribedText, copiedMessage)
                            },
                            onDeleteClick = {
                                viewModel.deleteTranscription(item)
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog Sheet
    selectedTranscription?.let { entity ->
        TranscriptionDetailDialog(
            entity = entity,
            onDismiss = { viewModel.selectTranscription(null) },
            onCopyClick = { copyToClipboard(context, entity.transcribedText, copiedMessage) }
        )
    }

    // Model Missing Dialog
    if (showModelMissingDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelDialog() },
            title = { Text(text = stringResource(R.string.dialog_model_required_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_model_required_body,
                        selectedModel.displayName,
                        selectedModel.sizeLabel
                    )
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.downloadModel() }) {
                    Text(text = stringResource(R.string.download_model))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissModelDialog() }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // Model Picker Bottom Sheet
    if (showModelPicker) {
        ModelPickerSheet(
            models = availableModels,
            selectedModelId = selectedModel.id,
            downloadStatus = modelDownloadStatus,
            isDownloading = isDownloadingModel,
            downloadingModelId = if (isDownloadingModel) selectedModel.id else null,
            downloadProgress = downloadProgress,
            selectedLanguage = transcriptionLanguage,
            onSelectModel = { model ->
                viewModel.selectModel(model)
            },
            onSelectLanguage = { language ->
                viewModel.setTranscriptionLanguage(language)
            },
            onDownloadModel = {
                viewModel.downloadModel()
            },
            onDeleteModel = { model ->
                viewModel.deleteModel(model)
            },
            onDismiss = { showModelPicker = false }
        )
    }

    // Log Viewer Bottom Sheet
    val noLogsText = stringResource(R.string.no_logs)
    val logShareSubject = stringResource(R.string.log_share_subject)
    val logClipLabel = stringResource(R.string.log_clip_label)
    val shareLogsTitle = stringResource(R.string.share_logs)
    val shareLogsError = stringResource(R.string.err_share_logs)

    if (showLogSheet) {
        LogViewerSheet(
            onDismiss = { showLogSheet = false },
            onCopyLogs = {
                val logsText = AppLogger.getAllLogsText()
                copyToClipboard(context, if (logsText.isBlank()) noLogsText else logsText, copiedMessage)
            },
            onShareLogs = {
                try {
                    val logFile = AppLogger.getLogFile()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, logShareSubject)
                        if (logFile != null && logFile.exists() && logFile.length() > 0) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                logFile
                            )
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri(logClipLabel, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            putExtra(
                                Intent.EXTRA_TEXT,
                                AppLogger.getAllLogsText().ifBlank { noLogsText }
                            )
                        }
                    }
                    context.startActivity(Intent.createChooser(shareIntent, shareLogsTitle))
                } catch (e: Exception) {
                    AppLogger.e("MainScreen", "Error sharing logs: ${e.message}", e)
                    Toast.makeText(context, shareLogsError, Toast.LENGTH_SHORT).show()
                }
            },
            onClearLogs = { AppLogger.clear() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerSheet(
    onDismiss: () -> Unit,
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logs by AppLogger.logs.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.logs_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.log_count, logs.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row {
                    IconButton(onClick = onShareLogs) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(R.string.share_logs))
                    }
                    IconButton(onClick = onCopyLogs) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_logs_cd))
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.clear_logs_cd))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFF1E1E1E)
            ) {
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_logs_yet),
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items = logs, key = { log -> log.id }) { log ->
                            val textColor = when (log.level) {
                                LogLevel.INFO -> Color(0xFFD4D4D4)
                                LogLevel.WARN -> Color(0xFFFFCC00)
                                LogLevel.ERROR -> Color(0xFFFF5555)
                            }

                            Text(
                                text = log.formattedText(),
                                color = textColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.close_logs))
            }
        }
    }
}

@Composable
fun ModelStatusCard(
    selectedModel: WhisperModelDef,
    isModelDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    languageLabel: String,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isModelDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isModelDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isModelDownloaded) stringResource(R.string.model_ready) else stringResource(R.string.model_not_downloaded),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isModelDownloaded)
                            stringResource(R.string.model_ready_details, selectedModel.displayName, selectedModel.sizeLabel, languageLabel)
                        else
                            stringResource(R.string.model_missing_details, selectedModel.displayName, selectedModel.sizeLabel),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = stringResource(R.string.change),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.downloading_model_percent, selectedModel.displayName, (progress * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall
                )
            } else if (!isModelDownloaded) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.download_model_size, selectedModel.displayName, selectedModel.sizeLabel))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelPickerSheet(
    models: List<WhisperModelDef>,
    selectedModelId: String,
    downloadStatus: Map<String, Boolean>,
    isDownloading: Boolean,
    downloadingModelId: String?,
    downloadProgress: Float,
    selectedLanguage: TranscriptionLanguage,
    onSelectModel: (WhisperModelDef) -> Unit,
    onSelectLanguage: (TranscriptionLanguage) -> Unit,
    onDownloadModel: () -> Unit,
    onDeleteModel: (WhisperModelDef) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.pick_model_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.pick_model_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            models.forEach { model ->
                ModelPickerItem(
                    model = model,
                    isSelected = model.id == selectedModelId,
                    isDownloaded = downloadStatus[model.id] == true,
                    isDownloadingThis = isDownloading && downloadingModelId == model.id,
                    isAnyDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    onSelectModel = onSelectModel,
                    onDownloadModel = onDownloadModel,
                    onDeleteModel = onDeleteModel
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.transcription_language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TranscriptionLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = language == selectedLanguage,
                        onClick = { onSelectLanguage(language) },
                        label = { Text(text = stringResource(language.labelRes)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ModelPickerItem(
    model: WhisperModelDef,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloadingThis: Boolean,
    isAnyDownloading: Boolean,
    downloadProgress: Float,
    onSelectModel: (WhisperModelDef) -> Unit,
    onDownloadModel: () -> Unit,
    onDeleteModel: (WhisperModelDef) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isDownloadingThis -> Icons.Default.Download
                    isDownloaded -> Icons.Default.CheckCircle
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when {
                    isDownloadingThis -> MaterialTheme.colorScheme.primary
                    isDownloaded -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (model.recommended) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = stringResource(R.string.recommended),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.in_use),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(
                        R.string.model_specs,
                        model.displayName,
                        stringResource(model.speedLabelRes),
                        model.minRamGb
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isDownloadingThis) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.downloading_percent, (downloadProgress * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (isDownloadingThis) {
                // Download in progress: no actions
            } else if (isDownloaded) {
                if (!isSelected) {
                    TextButton(onClick = { onSelectModel(model) }) {
                        Text(text = stringResource(R.string.use))
                    }
                    IconButton(onClick = { onDeleteModel(model) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_model_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (!isAnyDownloading) {
                TextButton(onClick = {
                    onSelectModel(model)
                    onDownloadModel()
                }) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.download))
                }
            }
        }
    }
}

@Composable
fun TranscriptionCard(
    item: TranscriptionEntity,
    onClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(item.timestamp))
    val status = try {
        TranscriptionStatus.valueOf(item.status)
    } catch (_: IllegalArgumentException) {
        TranscriptionStatus.DONE
    }
    val isDone = status == TranscriptionStatus.DONE
    val isQueued = status == TranscriptionStatus.QUEUED
    val isProcessing = status == TranscriptionStatus.PROCESSING
    val isError = status == TranscriptionStatus.ERROR

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isQueued || isProcessing) Modifier else Modifier.clickable { onClick() }),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    isQueued -> StatusChip(
                        text = stringResource(R.string.status_queued),
                        icon = { Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    )
                    isProcessing -> StatusChip(
                        text = stringResource(R.string.status_processing),
                        icon = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp
                            )
                        }
                    )
                    isError -> StatusChip(
                        text = stringResource(R.string.status_error),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                    else -> item.durationSeconds?.let { dur ->
                        val min = dur / 60
                        val sec = dur % 60
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", min, sec),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            item.displayName?.let { name ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                isQueued || isProcessing -> Unit
                isError -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.transcribedText.ifBlank { stringResource(R.string.status_error_generic) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.transcribedText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isDone) {
                    IconButton(onClick = onCopyClick) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_text_cd)
                        )
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_cd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun TranscriptionDetailDialog(
    entity: TranscriptionEntity,
    onDismiss: () -> Unit,
    onCopyClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    val dateStr = dateFormat.format(Date(entity.timestamp))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = stringResource(R.string.detail_title))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                (entity.displayName ?: entity.audioFilename)?.let { name ->
                    Text(
                        text = stringResource(R.string.file_label, name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entity.transcribedText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onCopyClick) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.copy_text))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String, copiedMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Awedio Transcription", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
}
