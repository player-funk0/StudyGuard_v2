package com.obrynex.studyguard.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.obrynex.studyguard.ui.theme.*

/**
 * AI Tutor screen.
 *
 * Changes vs. previous version:
 *  - Retry button disabled while [AiTutorState.isRetrying] is true.
 *  - Elapsed load time shown in [ModelLoadingBanner].
 *  - Fallback UI banner shown when [AiTutorState.isFallbackMode] is true.
 *  - One-shot [AiTutorEvent]s (errors, fallback activation) shown as Snackbars.
 */
@Composable
fun AiTutorScreen(vm: AiTutorViewModel) {
    val s         by vm.state.collectAsState()
    val listState  = rememberLazyListState()
    val snackbar   = remember { SnackbarHostState() }

    // Drive engine start from UI lifecycle
    LaunchedEffect(Unit) { vm.startEngine() }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is AiTutorEvent.InferenceError   ->
                    snackbar.showSnackbar("خطأ في الاستدلال: ${event.message}", duration = SnackbarDuration.Long)
                is AiTutorEvent.FallbackActivated ->
                    snackbar.showSnackbar("تم التفعيل في الوضع البديل — يُعاد تحميل النموذج…")
            }
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(s.messages.size) {
        if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.lastIndex)
    }

    Scaffold(
        containerColor      = BgDark,
        snackbarHost        = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0)   // outer NavGraph Scaffold owns system-bar insets
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
                .imePadding()   // keyboard slides up: shrinks the chat area, not the input bar
        ) {
            ScreenHeader(s = s, onClear = vm::clear)
            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            // Fallback mode banner (non-blocking — chat still usable)
            if (s.isFallbackMode) {
                FallbackModeBanner()
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
            }

            when (val modelState = s.modelState) {

                is AIModelState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color       = AccentGreen,
                            modifier    = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                is AIModelState.Validating,
                is AIModelState.Loading -> {
                    ModelLoadingBanner(
                        isHashing  = modelState is AIModelState.Validating,
                        loadTimeMs = s.loadTimeMs
                    )
                }

                is AIModelState.NotFound -> {
                    ModelSetupBanner(
                        modelPath = s.modelPath,
                        onRetry   = vm::retryEngine,
                        isRetrying = s.isRetrying
                    )
                }

                is AIModelState.Failed -> {
                    ModelFailedBanner(
                        failure    = modelState.reason,
                        onRetry    = vm::retryEngine,
                        isRetrying = s.isRetrying
                    )
                }

                is AIModelState.Ready -> {
                    SubjectChipRow(subject = s.subject, onSubjectChange = vm::onSubjectChanged)
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)

                    if (s.messages.isEmpty()) {
                        EmptyChat(Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            state               = listState,
                            modifier            = Modifier.weight(1f),
                            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(s.messages) { msg -> MessageBubble(msg) }
                        }
                    }

                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    InputBar(
                        input         = s.input,
                        isGenerating  = s.isGenerating,
                        onInputChange = vm::onInputChanged,
                        onSend        = vm::send
                    )
                }
            }
        }
    }
}

// ── Shared header ─────────────────────────────────────────────────────────────

@Composable
private fun ScreenHeader(s: AiTutorState, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "مساعد الدراسة",
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(
                            when (s.modelState) {
                                is AIModelState.Ready      -> AccentGreen
                                is AIModelState.Validating,
                                is AIModelState.Loading    -> AccentGreen.copy(alpha = 0.4f)
                                else                       -> TextMuted
                            }
                        )
                )
                Text(
                    text = when {
                        s.isFallbackMode               -> "وضع بديل · TextRank"
                        s.modelState is AIModelState.Ready      -> "Gemma 2B · محلي"
                        s.modelState is AIModelState.Validating -> "يتحقق…"
                        s.modelState is AIModelState.Loading    -> "يُحمِّل…"
                        s.modelState is AIModelState.Failed     -> "فشل التحميل"
                        s.modelState is AIModelState.NotFound   -> "النموذج غير موجود"
                        else                                     -> "في الانتظار"
                    },
                    color    = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
        if (s.messages.isNotEmpty() && s.modelState is AIModelState.Ready) {
            TextButton(
                onClick        = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("مسح", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

// ── Fallback mode banner ──────────────────────────────────────────────────────

@Composable
private fun FallbackModeBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFF2A2010))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint     = androidx.compose.ui.graphics.Color(0xFFFFB74D),
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                "وضع بديل نشط",
                color      = androidx.compose.ui.graphics.Color(0xFFFFB74D),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "النموذج يُعاد تحميله في الخلفية. الردود مؤقتاً من المُلخِّص المحلي.",
                color    = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ── Loading banner ────────────────────────────────────────────────────────────

@Composable
private fun ModelLoadingBanner(isHashing: Boolean, loadTimeMs: Long) {
    // Elapsed time counter — increments every second while loading
    var elapsedSecs by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            elapsedSecs++
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator(
            color       = AccentGreen,
            modifier    = Modifier.size(40.dp),
            strokeWidth = 2.5.dp
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text       = if (isHashing) "يتحقق من الملف…" else "يُحمِّل النموذج…",
                color      = TextPrimary,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium
            )
            // Show elapsed time counter
            Text(
                text     = "مضى: ${elapsedSecs}ث",
                color    = TextMuted,
                fontSize = 12.sp
            )
            if (!isHashing) {
                Text(
                    "قد تستغرق العملية دقيقة أو أكثر…",
                    color    = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
        // Show the last successful load time as a hint
        if (loadTimeMs > 0) {
            Text(
                "آخر تحميل: ${loadTimeMs / 1000}ث",
                color    = TextMuted.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

// ── Setup banner (file not found) ─────────────────────────────────────────────

@Composable
private fun ModelSetupBanner(
    modelPath  : String,
    onRetry    : () -> Unit,
    isRetrying : Boolean
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "إعداد الذكاء الاصطناعي",
            color      = TextPrimary,
            fontSize   = 17.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            "النموذج يعمل 100% على جهازك — بدون إنترنت ولا أي تكلفة.",
            color      = TextMuted,
            fontSize   = 13.sp,
            lineHeight = 22.sp
        )

        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        Text("خطوات الإعداد (مرة واحدة فقط):", color = TextMuted, fontSize = 12.sp)

        listOf(
            "1" to "روح kaggle.com/models/google/gemma/tfLite/gemma-2b-it-cpu-int4",
            "2" to "حمّل ملف gemma-2b-it-cpu-int4.bin (حجمه ~1.3 جيجا)",
            "3" to "انسخه على جهازك بالـ USB أو adb:"
        ).forEach { (num, step) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(num, color = Accent.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(step, color = TextMuted, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .padding(12.dp)
        ) {
            Text(
                "adb push gemma-2b-it-cpu-int4.bin \"$modelPath\"",
                color      = AccentGreen,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Text("بعد النسخ، اضغط إعادة المحاولة.", color = TextMuted, fontSize = 12.sp)

        // Retry button — disabled while isRetrying to prevent double-tap
        Button(
            onClick  = onRetry,
            enabled  = !isRetrying,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Accent,
                disabledContainerColor = Surface2
            )
        ) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    color       = TextMuted,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("جارٍ التحميل…", color = TextMuted)
            } else {
                Text("إعادة المحاولة", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

// ── Failed banner ─────────────────────────────────────────────────────────────

@Composable
private fun ModelFailedBanner(
    failure    : ValidationFailure,
    onRetry    : () -> Unit,
    isRetrying : Boolean
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "فشل تحميل النموذج",
            color      = androidx.compose.ui.graphics.Color(0xFFE57373),
            fontSize   = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Surface1)
                .padding(14.dp)
        ) {
            Text(
                text       = failure.toArabicMessage(),
                color      = TextPrimary,
                fontSize   = 13.sp,
                lineHeight = 22.sp
            )
        }

        when (failure) {
            is ValidationFailure.SizeTooSmall ->
                Text(
                    "الملف حجمه ${failure.actualBytes / 1_000_000} MB — " +
                    "أصغر من الحد الأدنى ${failure.minimumBytes / 1_000_000} MB.\n" +
                    "أعد التحميل من Kaggle وتأكد اكتمل الملف.",
                    color = TextMuted, fontSize = 12.sp, lineHeight = 20.sp
                )
            is ValidationFailure.ChecksumMismatch ->
                Text(
                    "الهاش المتوقع: …${failure.expected.takeLast(12)}\n" +
                    "الهاش الفعلي: …${failure.actual.takeLast(12)}",
                    color      = TextMuted,
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            is ValidationFailure.LoadFailed ->
                Text(
                    failure.cause.message ?: "Unknown MediaPipe error",
                    color      = TextMuted,
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            is ValidationFailure.InsufficientRam ->
                Text(
                    "أغلق بعض التطبيقات الأخرى لتوفير ذاكرة إضافية ثم حاول مجدداً.",
                    color = TextMuted, fontSize = 12.sp, lineHeight = 20.sp
                )
            else -> Unit
        }

        // Retry button — disabled while retrying
        Button(
            onClick  = onRetry,
            enabled  = !isRetrying,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Accent,
                disabledContainerColor = Surface2
            )
        ) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    color       = TextMuted,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("جارٍ التحميل…", color = TextMuted)
            } else {
                Text("إعادة المحاولة", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

// ── Subject chips ─────────────────────────────────────────────────────────────

@Composable
private fun SubjectChipRow(subject: String, onSubjectChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("المادة:", color = TextMuted, fontSize = 12.sp)
        listOf("عام", "رياضيات", "فيزياء", "كيمياء", "أحياء", "تاريخ").forEach { sub ->
            val active = subject == sub || (sub == "عام" && subject.isEmpty())
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) Surface3 else Surface1)
                    .clickable { onSubjectChange(if (sub == "عام") "" else sub) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(sub, color = if (active) TextPrimary else TextMuted, fontSize = 11.sp)
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("اسألني أي سؤال", color = TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "الذكاء الاصطناعي يعمل على جهازك بالكامل",
                color    = TextMuted.copy(0.5f),
                fontSize = 12.sp
            )
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    input        : String,
    isGenerating : Boolean,
    onInputChange: (String) -> Unit,
    onSend       : () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value         = input,
            onValueChange = onInputChange,
            modifier      = Modifier.weight(1f),
            placeholder   = { Text("اكتب سؤالك…", color = TextMuted, fontSize = 13.sp) },
            maxLines      = 4,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = Accent.copy(alpha = 0.5f),
                unfocusedBorderColor    = Surface2,
                focusedTextColor        = TextPrimary,
                unfocusedTextColor      = TextPrimary,
                cursorColor             = Accent,
                focusedContainerColor   = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp)
        )
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (input.isNotBlank() && !isGenerating) Accent else Surface2)
                .clickable(enabled = input.isNotBlank() && !isGenerating) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    color       = TextMuted,
                    strokeWidth = 1.5.dp
                )
            } else {
                Icon(
                    Icons.Default.Send, null,
                    tint     = if (input.isNotBlank())
                        androidx.compose.ui.graphics.Color.White else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.75f)   // 75 % of row width — adaptive on phones, tablets, foldables
                .clip(
                    RoundedCornerShape(
                        topStart    = if (msg.isUser) 14.dp else 4.dp,
                        topEnd      = if (msg.isUser) 4.dp else 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd   = 14.dp
                    )
                )
                .background(
                    when {
                        msg.isUser     -> Accent.copy(alpha = 0.15f)
                        msg.isFallback -> Surface1.copy(alpha = 0.7f)
                        else           -> Surface1
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (msg.isLoading) LoadingDots()
            else Text(msg.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

// ── Loading dots animation ────────────────────────────────────────────────────

@Composable
private fun LoadingDots() {
    val inf   = rememberInfiniteTransition(label = "dots")
    val alpha by inf.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "a"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(TextMuted.copy(alpha = alpha - it * 0.1f))
            )
        }
    }
}
