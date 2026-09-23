package com.diego.awedio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.diego.awedio.data.TranscriptionEntity
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
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val showModelMissingDialog by viewModel.showModelMissingDialog.collectAsState()

    var showLogSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Awedio",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Transcritor Offline de Notas de Voz",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    IconButton(onClick = { showLogSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Logs de Sistema"
                        )
                    }
                    if (transcriptions.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Limpar Histórico"
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
                isModelDownloaded = isModelDownloaded,
                isDownloading = isDownloadingModel,
                progress = downloadProgress,
                onDownloadClick = { viewModel.downloadModel() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status Message / Active Processing Banner
            statusMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTranscribing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                            Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!isTranscribing) {
                            IconButton(onClick = { viewModel.clearStatusMessage() }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
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
                    text = "Histórico de Transcrições",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${transcriptions.size} item(s)",
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
                            text = "Nenhuma transcrição ainda.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compartilhe um áudio do WhatsApp com o Awedio!",
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
                                copyToClipboard(context, item.transcribedText)
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
            onCopyClick = { copyToClipboard(context, entity.transcribedText) }
        )
    }

    // Model Missing Dialog
    if (showModelMissingDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelDialog() },
            title = { Text(text = "Modelo Whisper Necessário") },
            text = {
                Text(
                    text = "Para transcrever áudios offline, você precisa baixar o modelo Whisper base (~142MB) uma única vez. Deseja baixar agora?"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.downloadModel() }) {
                    Text(text = "Baixar Modelo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissModelDialog() }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    // Log Viewer Bottom Sheet
    if (showLogSheet) {
        LogViewerSheet(
            onDismiss = { showLogSheet = false },
            onCopyLogs = {
                val logsText = AppLogger.getAllLogsText()
                copyToClipboard(context, if (logsText.isBlank()) "Sem logs registrados." else logsText)
            },
            onShareLogs = {
                try {
                    val logFile = AppLogger.getLogFile()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Awedio - Logs do Sistema")
                        if (logFile != null && logFile.exists() && logFile.length() > 0) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                logFile
                            )
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("Awedio Logs", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            putExtra(
                                Intent.EXTRA_TEXT,
                                AppLogger.getAllLogsText().ifBlank { "Sem logs registrados." }
                            )
                        }
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Logs"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao compartilhar logs: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        text = "Logs do Sistema",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${logs.size} linha(s) de registro",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row {
                    IconButton(onClick = onShareLogs) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Compartilhar Logs")
                    }
                    IconButton(onClick = onCopyLogs) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar Logs")
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Limpar Logs")
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
                            text = "Nenhum log registrado ainda.",
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
                Text(text = "Fechar Logs")
            }
        }
    }
}

@Composable
fun ModelStatusCard(
    isModelDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isModelDownloaded)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isModelDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isModelDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isModelDownloaded) "Modelo Whisper Pronto (Offline)" else "Modelo Whisper Não Baixado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isModelDownloaded)
                            "ggml-base.bin instalado • Transcrição em Português"
                        else
                            "Requer download de ~142MB do Hugging Face",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Baixando modelo: ${(progress * 100).toInt()}%",
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
                    Text(text = "Baixar Modelo Base (~142MB)")
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                item.durationSeconds?.let { dur ->
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

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.transcribedText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onCopyClick) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar Texto"
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir"
                    )
                }
            }
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
                Text(text = "Detalhes da Transcrição")
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
                entity.audioFilename?.let { name ->
                    Text(
                        text = "Arquivo: $name",
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
                Text(text = "Copiar Texto")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Fechar")
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Awedio Transcription", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Texto copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
}
