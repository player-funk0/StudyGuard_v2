package com.obrynex.studyguard.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obrynex.studyguard.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug / Diagnostics screen — shows AI engine state, hash, load time, errors,
 * and device RAM info.
 *
 * New features vs. previous version:
 *  - Copy-to-clipboard button for hash and error text.
 *  - Manual "Force Re-init" button (with loading state).
 *  - Device RAM section (total, available, low-memory flag).
 */
@Composable
fun DebugInfoScreen(vm: DebugInfoViewModel) {
    val s       by vm.state.collectAsState()
    val scroll  = rememberScrollState()
    val ctx     = LocalContext.current
    val snack   = remember { SnackbarHostState() }

    Scaffold(
        containerColor      = BgDark,
        snackbarHost        = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0)   // outer NavGraph Scaffold owns system-bar insets
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .verticalScroll(scroll)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Header ─────────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "AI Engine Diagnostics",
                    color      = TextPrimary,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextMuted)
                }
            }

            Text(
                "Debug build only — not visible in production.",
                color    = TextMuted,
                fontSize = 11.sp
            )

            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // ── Engine state ───────────────────────────────────────────────────
            DiagRow(
                label = "Engine State",
                value = s.stateName,
                valueColor = when (s.stateName) {
                    "Ready"                 -> AccentGreen
                    "Failed", "NotFound"    -> Color(0xFFE57373)
                    "Validating", "Loading" -> Color(0xFFFFB74D)
                    else                    -> TextMuted
                }
            )

            // ── Load time ──────────────────────────────────────────────────────
            DiagRow(
                label = "Load Time",
                value = if (s.loadTimeMs >= 0) "${s.loadTimeMs} ms" else "—"
            )

            // ── In-memory hash ─────────────────────────────────────────────────
            DiagSection(
                title   = "In-Memory Hash (this session)",
                content = s.hash ?: "—",
                onCopy  = {
                    s.hash?.let { copyToClipboard(ctx, "hash", it) }
                    snack.tryShowSnackbar("Hash copied to clipboard")
                }
            )

            // ── Persisted hash ─────────────────────────────────────────────────
            DiagSection(
                title   = "Cached Hash (DataStore)",
                content = s.cachedHash ?: "—",
                onCopy  = {
                    s.cachedHash?.let { copyToClipboard(ctx, "cached_hash", it) }
                    snack.tryShowSnackbar("Cached hash copied to clipboard")
                }
            )

            // ── File snapshot ──────────────────────────────────────────────────
            DiagRow(
                label = "Cached File Size",
                value = if (s.cachedSize >= 0) "${s.cachedSize / 1_000_000L} MB" else "—"
            )
            DiagRow(
                label = "Cached Last-Modified",
                value = if (s.cachedModified >= 0)
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(s.cachedModified))
                else "—"
            )

            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // ── Device RAM ─────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                Text("Device RAM", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            DiagRow(
                label = "Total RAM",
                value = if (s.totalRamMb >= 0) "${s.totalRamMb} MB" else "—"
            )
            DiagRow(
                label = "Available RAM",
                value = if (s.availRamMb >= 0) "${s.availRamMb} MB" else "—",
                valueColor = when {
                    s.availRamMb < 0   -> TextMuted
                    s.isLowMemory      -> Color(0xFFE57373)
                    s.availRamMb < 512 -> Color(0xFFFFB74D)
                    else               -> AccentGreen
                }
            )
            if (s.isLowMemory) {
                Text(
                    "⚠ Low memory — model load may fail",
                    color    = Color(0xFFE57373),
                    fontSize = 11.sp
                )
            }

            // ── Error ──────────────────────────────────────────────────────────
            s.errorMessage?.let { err ->
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Error", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    IconButton(
                        onClick  = {
                            copyToClipboard(ctx, "error", err)
                            snack.tryShowSnackbar("Error copied to clipboard")
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface1)
                        .padding(12.dp)
                ) {
                    Text(
                        text       = err,
                        color      = Color(0xFFEF9A9A),
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }

            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // ── Force Re-init ──────────────────────────────────────────────────
            OutlinedButton(
                onClick  = vm::forceReInit,
                enabled  = !s.isForceReiniting,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                border   = androidx.compose.foundation.BorderStroke(
                    1.dp, if (s.isForceReiniting) Surface2 else Color(0xFFFFB74D)
                )
            ) {
                if (s.isForceReiniting) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        color       = TextMuted,
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Re-initialising…", color = TextMuted, fontSize = 13.sp)
                } else {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Force Re-init", fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Internal composables ──────────────────────────────────────────────────────

@Composable
private fun DiagRow(
    label      : String,
    value      : String,
    valueColor : Color = TextPrimary
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DiagSection(
    title   : String,
    content : String,
    onCopy  : (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(title, color = TextMuted, fontSize = 12.sp)
            if (onCopy != null && content != "—") {
                IconButton(
                    onClick  = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .padding(10.dp)
        ) {
            Text(
                text       = content,
                color      = TextPrimary,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Fire-and-forget snackbar show — ignores the result. */
private fun SnackbarHostState.tryShowSnackbar(message: String) {
    // We can't call showSnackbar (suspend) from a regular lambda,
    // so we rely on the coroutine scope available in the calling LaunchedEffect.
    // The onCopy lambda is called from a click handler, so we post to the current
    // coroutine scope by using GlobalScope as a best-effort workaround for the
    // clipboard-only feedback case.  Production code should thread a scope through.
    kotlinx.coroutines.MainScope().launch { showSnackbar(message) }
}

// Needed for tryShowSnackbar
private fun <T> kotlinx.coroutines.CoroutineScope.launch(
    block: suspend kotlinx.coroutines.CoroutineScope.() -> T
) = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    .launch(block = block)
