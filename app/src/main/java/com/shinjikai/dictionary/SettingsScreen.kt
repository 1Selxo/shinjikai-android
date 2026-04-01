package com.shinjikai.dictionary

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shinjikai.dictionary.ui.Screen
import com.shinjikai.dictionary.ui.SettingsUiState
import com.shinjikai.dictionary.ui.ShinjikaiViewModel

private data class OfflineImportStatusUi(
    val label: String,
    val color: Color
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreenContent(
    appVersionLabel: String,
    supportsDynamicColor: Boolean,
    uiState: SettingsUiState,
    viewModel: ShinjikaiViewModel,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${stringResource(R.string.settings_about_version)}: $appVersionLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingsToggleRow(
                        icon = Icons.Filled.DarkMode,
                        label = stringResource(R.string.settings_dark_mode),
                        checked = uiState.settings.darkMode,
                        onCheckedChange = viewModel::setDarkMode
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Palette,
                        label = stringResource(R.string.settings_dynamic_color),
                        checked = uiState.settings.useDynamicColor && supportsDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor,
                        enabled = supportsDynamicColor
                    )
                }
            }

            LocalDictionarySummaryCard(
                uiState = uiState,
                onClick = { viewModel.navigateTo(Screen.LocalDictionary) }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingsSectionTitle(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_about_title)
                    )
                    SettingsLinkRow(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.settings_attribution_title),
                        description = stringResource(R.string.settings_attribution_description),
                        contentDescription = stringResource(R.string.settings_attribution_open),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://shinjikai.app")
                                )
                            )
                        }
                    )
                    SettingsLinkRow(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.settings_github),
                        description = stringResource(R.string.settings_about_source_description),
                        contentDescription = stringResource(R.string.settings_open_github),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/obj44/shinjikai")
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LocalDictionaryScreenContent(
    uiState: SettingsUiState,
    onPickOfflineZip: () -> Unit,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_local_dictionary)) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OfflineImporterCard(
                uiState = uiState,
                onPickOfflineZip = onPickOfflineZip,
                onOpenDownloads = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/obj44/shinjikai/releases/tag/dict-v1")
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun LocalDictionarySummaryCard(
    uiState: SettingsUiState,
    onClick: () -> Unit
) {
    val hasOfflineDictionary = uiState.offlineTermCount > 0
    val statusUi = offlineImportStatusUi(uiState, hasOfflineDictionary)
    val summary = when {
        uiState.isImportingOfflineData ->
            uiState.offlineImportPhase ?: stringResource(R.string.settings_loading_inline)
        uiState.offlineImportError ->
            uiState.offlineImportStatus ?: stringResource(R.string.offline_import_failure)
        hasOfflineDictionary ->
            stringResource(R.string.settings_local_count, uiState.offlineTermCount)
        else ->
            "افتح هذه الصفحة لتثبيت القاموس المحلي وإدارة الاستيراد."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            SettingsLeadingIcon(icon = Icons.Filled.DownloadForOffline)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_local_dictionary),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                        label = statusUi.label,
                        color = statusUi.color
                    )
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.settings_local_dictionary)
            )
        }
    }
}

@Composable
private fun OfflineImporterCard(
    uiState: SettingsUiState,
    onPickOfflineZip: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val hasOfflineDictionary = uiState.offlineTermCount > 0
    val statusColor = when {
        uiState.isImportingOfflineData -> MaterialTheme.colorScheme.primary
        uiState.offlineImportError -> MaterialTheme.colorScheme.error
        hasOfflineDictionary -> Color(0xFF1F8A55)
        else -> MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when {
        uiState.isImportingOfflineData -> "جاري الاستيراد"
        uiState.offlineImportError -> "تحتاج العملية إلى إعادة المحاولة"
        hasOfflineDictionary -> "جاهز للاستخدام بدون إنترنت"
        else -> "لم يتم تثبيت القاموس بعد"
    }
    val summaryText = when {
        uiState.isImportingOfflineData ->
            uiState.offlineImportPhase ?: stringResource(R.string.settings_loading_inline)
        hasOfflineDictionary ->
            "تم تثبيت ${uiState.offlineTermCount} مدخل محلي ويمكنك استخدام البحث بدون اتصال."
        else ->
            "اختر أرشيف `.zip` أو `.tar.xz` لاستيراد النصوص والصور محلياً."
    }
    val latestUpdateText = uiState.offlineLastImportEpochMs?.let(::formatEpochAsLocal) ?: "لم يتم الاستيراد بعد"
    val latestSourceText = uiState.offlineLastImportSource?.let(::formatImportSourceName) ?: "لا يوجد مصدر مسجل بعد"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DownloadForOffline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_local_dictionary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    StatusBadge(
                        label = statusLabel,
                        color = statusColor
                    )
                }
            }

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )

            ImporterMetricRow(
                title = stringResource(R.string.settings_local_count, uiState.offlineTermCount),
                subtitle = "عدد المداخل المتاحة للبحث بدون اتصال"
            )

            ImporterMetricRow(
                title = "آخر تحديث",
                subtitle = latestUpdateText
            )

            ImporterMetricRow(
                title = "المصدر الأخير",
                subtitle = latestSourceText
            )

            ImporterMetricRow(
                title = "الملفات المدعومة",
                subtitle = "ZIP و TAR.XZ للنصوص والصور"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickOfflineZip,
                    enabled = !uiState.isImportingOfflineData,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isImportingOfflineData) {
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(text = "جاري الفهرسة..")
                            }
                    } else {
                        Text("استيراد ملف")
                    }
                }
                OutlinedButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Text(stringResource(R.string.settings_offline_archives_title))
                }
            }

            if (uiState.isImportingOfflineData) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.offlineImportPhase ?: stringResource(R.string.settings_loading_inline),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${(uiState.offlineImportProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { uiState.offlineImportProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            uiState.offlineImportStatus?.let { status ->
                StatusMessage(
                    message = status,
                    isError = uiState.offlineImportError
                )
            }
        }
    }
}

private fun formatImportSourceName(source: String): String {
    return when (source) {
        "japanesearabic-yomitan-v2" -> "Yomitan V2"
        "raw-shinjikai-jp-ar-picked-zip" -> "Raw Shinjikai (ZIP)"
        "raw-shinjikai-jp-ar-picked-tar-xz" -> "Raw Shinjikai (TAR.XZ)"
        "raw-shinjikai-jp-ar-jsonl" -> "Raw Shinjikai (JSONL)"
        else -> source
    }
}

@Composable
private fun offlineImportStatusUi(
    uiState: SettingsUiState,
    hasOfflineDictionary: Boolean = uiState.offlineTermCount > 0
): OfflineImportStatusUi {
    return when {
        uiState.isImportingOfflineData -> OfflineImportStatusUi(
            label = "جاري الاستيراد",
            color = MaterialTheme.colorScheme.primary
        )
        uiState.offlineImportError -> OfflineImportStatusUi(
            label = "تحتاج العملية إلى إعادة المحاولة",
            color = MaterialTheme.colorScheme.error
        )
        hasOfflineDictionary -> OfflineImportStatusUi(
            label = "جاهز للاستخدام بدون إنترنت",
            color = Color(0xFF1F8A55)
        )
        else -> OfflineImportStatusUi(
            label = "لم يتم تثبيت القاموس بعد",
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun StatusBadge(
    label: String,
    color: Color
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.TaskAlt,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ImporterMetricRow(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF1F8A55)
    val background = tint.copy(alpha = 0.12f)
    val icon = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint
                )
                if (isError) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message))
                            Toast.makeText(context, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "نسخ الرسالة",
                            color = tint,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    description: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsLeadingIcon(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsLeadingIcon(icon = icon)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SettingsSectionTitle(
    icon: ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsLeadingIcon(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
