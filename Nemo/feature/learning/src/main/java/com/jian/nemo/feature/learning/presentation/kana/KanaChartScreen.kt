package com.jian.nemo.feature.learning.presentation.kana

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.collectAsState
import com.jian.nemo.core.ui.component.common.CommonHeader
import com.jian.nemo.feature.learning.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class KanaCell(
    val hiragana: String,
    val katakana: String?,
    val romaji: String
)

private enum class KanaType {
    Hiragana,
    Katakana
}

private object KanaSectionIndex {
    const val Seion = 0
    const val Dakuon = 2
    const val Yoon = 5
    const val Sokuon = 8
    const val Chouon = 11
}

private val LightPrimaryChip = Color(0xFFDFF4FF)
private val LightAccent = Color(0xFF0EA5A8)
private val LightQuickNavBase = Color(0xFFF3FAFF)
private val LightQuickNavSelected = Color(0xFFD7F1FF)

@Composable
fun KanaChartScreen(
    onNavigateBack: () -> Unit,
    viewModel: KanaChartViewModel = hiltViewModel()
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val haptic = LocalHapticFeedback.current

    val backgroundColor = colorScheme.background
    val surfaceColor = if (isDark) colorScheme.surfaceContainer else colorScheme.surface
    val textMain = if (isDark) colorScheme.onSurface else Color(0xFF334155)
    val textSub = if (isDark) colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val accentColor = if (isDark) colorScheme.primary.copy(alpha = 0.7f) else LightAccent
    val tabContainerColor = if (isDark) colorScheme.surfaceContainer else Color(0xFFEAF6FF)
    val tabSelectedColor = if (isDark) colorScheme.surfaceContainerHigh else Color.White
    val quickNavBaseColor = if (isDark) surfaceColor else LightQuickNavBase
    val quickNavSelectedColor = if (isDark) colorScheme.primary.copy(alpha = 0.28f) else LightQuickNavSelected

    var currentType by remember { mutableIntStateOf(0) }
    var isQuickNavScrolling by remember { androidx.compose.runtime.mutableStateOf(false) }
    var flashingSection by remember { mutableStateOf<String?>(null) }
    val isKatakana = currentType == 1
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val playingAudioId by viewModel.playingAudioId.collectAsState()

    val sectionToIndex = remember {
        mapOf(
            "seion" to KanaSectionIndex.Seion,
            "dakuon" to KanaSectionIndex.Dakuon,
            "yoon" to KanaSectionIndex.Yoon,
            "sokuon" to KanaSectionIndex.Sokuon,
            "chouon" to KanaSectionIndex.Chouon
        )
    }
    val currentSection by remember {
        derivedStateOf {
            when (listState.firstVisibleItemIndex) {
                in 0..1 -> "seion"
                in 2..4 -> "dakuon"
                in 5..7 -> "yoon"
                in 8..10 -> "sokuon"
                else -> "chouon"
            }
        }
    }

    Scaffold(
        topBar = {
            CommonHeader(
                title = stringResource(R.string.kana_chart_title),
                onBack = onNavigateBack,
                backgroundColor = backgroundColor
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = tabContainerColor,
                    shadowElevation = if (isDark) 0.dp else 1.dp,
                    border = BorderStroke(
                        1.dp,
                        if (isDark) colorScheme.outline.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.85f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (currentType == KanaType.Hiragana.ordinal) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = tabSelectedColor,
                                    shadowElevation = if (isDark) 0.dp else 1.dp
                                ) {}
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = tabSelectedColor,
                                    shadowElevation = if (isDark) 0.dp else 1.dp
                                ) {}
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            KanaTypeButton(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.kana_tab_hiragana),
                                selected = currentType == 0,
                                textMain = textMain,
                                textSub = textSub,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentType = 0
                                }
                            )
                            KanaTypeButton(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.kana_tab_katakana),
                                selected = currentType == 1,
                                textMain = textMain,
                                textSub = textSub,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentType = 1
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val quickNavScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(quickNavScroll),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickNavButton(
                        label = stringResource(R.string.kana_quick_nav_seion),
                        surfaceColor = quickNavBaseColor,
                        selectedSurfaceColor = quickNavSelectedColor,
                        textMain = textMain,
                        selected = currentSection == "seion",
                        accentColor = accentColor,
                        isDark = isDark
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isQuickNavScrolling) return@QuickNavButton
                        scope.launch {
                            isQuickNavScrolling = true
                            try {
                                smartScrollToSection(listState, sectionToIndex.getValue("seion"))
                                flashingSection = "seion"
                                delay(280)
                                if (flashingSection == "seion") flashingSection = null
                            } finally {
                                isQuickNavScrolling = false
                            }
                        }
                    }
                    QuickNavButton(
                        label = stringResource(R.string.kana_quick_nav_dakuon),
                        surfaceColor = quickNavBaseColor,
                        selectedSurfaceColor = quickNavSelectedColor,
                        textMain = textMain,
                        selected = currentSection == "dakuon",
                        accentColor = accentColor,
                        isDark = isDark
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isQuickNavScrolling) return@QuickNavButton
                        scope.launch {
                            isQuickNavScrolling = true
                            try {
                                smartScrollToSection(listState, sectionToIndex.getValue("dakuon"))
                                flashingSection = "dakuon"
                                delay(280)
                                if (flashingSection == "dakuon") flashingSection = null
                            } finally {
                                isQuickNavScrolling = false
                            }
                        }
                    }
                    QuickNavButton(
                        label = stringResource(R.string.kana_quick_nav_yoon),
                        surfaceColor = quickNavBaseColor,
                        selectedSurfaceColor = quickNavSelectedColor,
                        textMain = textMain,
                        selected = currentSection == "yoon",
                        accentColor = accentColor,
                        isDark = isDark
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isQuickNavScrolling) return@QuickNavButton
                        scope.launch {
                            isQuickNavScrolling = true
                            try {
                                smartScrollToSection(listState, sectionToIndex.getValue("yoon"))
                                flashingSection = "yoon"
                                delay(280)
                                if (flashingSection == "yoon") flashingSection = null
                            } finally {
                                isQuickNavScrolling = false
                            }
                        }
                    }
                    QuickNavButton(
                        label = stringResource(R.string.kana_quick_nav_sokuon),
                        surfaceColor = quickNavBaseColor,
                        selectedSurfaceColor = quickNavSelectedColor,
                        textMain = textMain,
                        selected = currentSection == "sokuon",
                        accentColor = accentColor,
                        isDark = isDark
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isQuickNavScrolling) return@QuickNavButton
                        scope.launch {
                            isQuickNavScrolling = true
                            try {
                                smartScrollToSection(listState, sectionToIndex.getValue("sokuon"))
                                flashingSection = "sokuon"
                                delay(280)
                                if (flashingSection == "sokuon") flashingSection = null
                            } finally {
                                isQuickNavScrolling = false
                            }
                        }
                    }
                    QuickNavButton(
                        label = stringResource(R.string.kana_quick_nav_chouon),
                        surfaceColor = quickNavBaseColor,
                        selectedSurfaceColor = quickNavSelectedColor,
                        textMain = textMain,
                        selected = currentSection == "chouon",
                        accentColor = accentColor,
                        isDark = isDark
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isQuickNavScrolling) return@QuickNavButton
                        scope.launch {
                            isQuickNavScrolling = true
                            try {
                                smartScrollToSection(listState, sectionToIndex.getValue("chouon"))
                                flashingSection = "chouon"
                                delay(280)
                                if (flashingSection == "chouon") flashingSection = null
                            } finally {
                                isQuickNavScrolling = false
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SectionTitle(
                        text = stringResource(R.string.kana_section_seion),
                        accentColor = accentColor,
                        textMain = textMain,
                        flashing = flashingSection == "seion"
                    )
                }
                item {
                    KanaGrid(
                        cells = seionData,
                        columns = 5,
                        isKatakana = isKatakana,
                        surfaceColor = surfaceColor,
                        textMain = textMain,
                        textSub = textSub,
                        isDark = isDark,
                        playingAudioId = playingAudioId,
                        playingBorderColor = colorScheme.primary,
                        onSpeak = { speakText, id -> viewModel.speakKana(speakText, id) },
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    )
                }

                item {
                    SectionTitle(
                        text = stringResource(R.string.kana_section_dakuon),
                        accentColor = accentColor,
                        textMain = textMain,
                        flashing = flashingSection == "dakuon"
                    )
                }
                item {
                    SectionSubtitle(text = stringResource(R.string.kana_desc_dakuon), textSub = textSub)
                }
                item {
                    KanaGrid(
                        cells = dakuonData,
                        columns = 5,
                        isKatakana = isKatakana,
                        surfaceColor = surfaceColor,
                        textMain = textMain,
                        textSub = textSub,
                        isDark = isDark,
                        playingAudioId = playingAudioId,
                        playingBorderColor = colorScheme.primary,
                        onSpeak = { speakText, id -> viewModel.speakKana(speakText, id) },
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    )
                }

                item {
                    SectionTitle(
                        text = stringResource(R.string.kana_section_yoon),
                        accentColor = accentColor,
                        textMain = textMain,
                        flashing = flashingSection == "yoon"
                    )
                }
                item {
                    SectionSubtitle(text = stringResource(R.string.kana_desc_yoon), textSub = textSub)
                }
                item {
                    KanaGrid(
                        cells = yoonData,
                        columns = 3,
                        isKatakana = isKatakana,
                        surfaceColor = surfaceColor,
                        textMain = textMain,
                        textSub = textSub,
                        isDark = isDark,
                        playingAudioId = playingAudioId,
                        playingBorderColor = colorScheme.primary,
                        onSpeak = { speakText, id -> viewModel.speakKana(speakText, id) },
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    )
                }

                item {
                    SectionTitle(
                        accentColor = accentColor,
                        textMain = textMain,
                        flashing = flashingSection == "sokuon",
                        text = if (isKatakana) {
                            stringResource(R.string.kana_section_sokuon_katakana)
                        } else {
                            stringResource(R.string.kana_section_sokuon_hiragana)
                        }
                    )
                }
                item {
                    SectionSubtitle(text = stringResource(R.string.kana_desc_sokuon), textSub = textSub)
                }
                item {
                    KanaGrid(
                        cells = sokuonData,
                        columns = 4,
                        isKatakana = isKatakana,
                        surfaceColor = surfaceColor,
                        textMain = textMain,
                        textSub = textSub,
                        isDark = isDark,
                        playingAudioId = playingAudioId,
                        playingBorderColor = colorScheme.primary,
                        onSpeak = { speakText, id -> viewModel.speakKana(speakText, id) },
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    )
                }

                item {
                    SectionTitle(
                        text = stringResource(R.string.kana_section_chouon),
                        accentColor = accentColor,
                        textMain = textMain,
                        flashing = flashingSection == "chouon"
                    )
                }
                item {
                    KanaGrid(
                        cells = chouonData,
                        columns = 5,
                        isKatakana = isKatakana,
                        surfaceColor = surfaceColor,
                        textMain = textMain,
                        textSub = textSub,
                        isDark = isDark,
                        playingAudioId = playingAudioId,
                        playingBorderColor = colorScheme.primary,
                        onSpeak = { speakText, id -> viewModel.speakKana(speakText, id) },
                        onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    )
                }
            }
        }
    }
}

private suspend fun smartScrollToSection(
    listState: androidx.compose.foundation.lazy.LazyListState,
    targetIndex: Int
) {
    val currentIndex = listState.firstVisibleItemIndex
    val distance = abs(targetIndex - currentIndex)

    // Top section is sensitive to pre-jump; use direct smooth animation.
    if (targetIndex == 0) {
        listState.animateScrollToItem(0)
        return
    }

    if (distance > 6 && targetIndex > currentIndex) {
        val preIndex = (targetIndex - 2).coerceAtLeast(0)
        listState.scrollToItem(preIndex)
    }
    listState.animateScrollToItem(targetIndex)
}

@Composable
private fun KanaTypeButton(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    textMain: Color,
    textSub: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (selected) textMain else textSub
        )
    }
}

@Composable
private fun QuickNavButton(
    label: String,
    surfaceColor: Color,
    selectedSurfaceColor: Color,
    textMain: Color,
    selected: Boolean,
    accentColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedSurfaceColor else surfaceColor,
        animationSpec = tween(180),
        label = "quickNavBg"
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) accentColor else textMain.copy(alpha = 0.9f),
        animationSpec = tween(180),
        label = "quickNavFg"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .noRippleClickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (isDark) 0.dp else 1.dp,
        border = BorderStroke(
            1.dp,
            if (selected) accentColor.copy(alpha = if (isDark) 0.32f else 0.34f)
            else if (isDark) Color.White.copy(alpha = 0.08f)
            else Color(0xFFDCEEFF)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fgColor
        )
    }
}

@Composable
private fun SectionTitle(text: String, accentColor: Color, textMain: Color, flashing: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = if (flashing) accentColor.copy(alpha = 0.75f) else Color.Transparent,
        animationSpec = tween(220),
        label = "sectionFlashBorder"
    )
    Surface(
        modifier = Modifier.padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = textMain
            )
        }
    }
}

@Composable
private fun SectionSubtitle(text: String, textSub: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = textSub
    )
}

@Composable
private fun KanaGrid(
    cells: List<KanaCell?>,
    columns: Int,
    isKatakana: Boolean,
    surfaceColor: Color,
    textMain: Color,
    textSub: Color,
    isDark: Boolean,
    playingAudioId: String?,
    playingBorderColor: Color,
    onSpeak: (String, String) -> Unit,
    onHaptic: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { cell ->
                    KanaCard(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp),
                        cell = cell,
                        isKatakana = isKatakana,
                        surfaceColor = surfaceColor,
                        textMain = textMain,
                        textSub = textSub,
                        isDark = isDark,
                        playingAudioId = playingAudioId,
                        playingBorderColor = playingBorderColor,
                        onSpeak = onSpeak,
                        onHaptic = onHaptic
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KanaCard(
    modifier: Modifier = Modifier,
    cell: KanaCell?,
    isKatakana: Boolean,
    surfaceColor: Color,
    textMain: Color,
    textSub: Color,
    isDark: Boolean,
    playingAudioId: String?,
    playingBorderColor: Color,
    onSpeak: (String, String) -> Unit,
    onHaptic: () -> Unit
) {
    if (cell == null || (isKatakana && cell.katakana == null)) {
        Spacer(modifier = modifier)
        return
    }

    val kanaText = if (isKatakana) cell.katakana.orEmpty() else cell.hiragana
    val cardAudioId = "kana_${kanaText.hashCode()}"
    val isPlaying = playingAudioId == cardAudioId
    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) playingBorderColor.copy(alpha = if (isDark) 0.88f else 0.78f) else Color.Transparent,
        animationSpec = tween(180),
        label = "cardPlayingBorder"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var clickPulse by remember { mutableStateOf(false) }

    LaunchedEffect(clickPulse) {
        if (clickPulse) {
            delay(95)
            clickPulse = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed || clickPulse) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
        label = "qqScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .noRippleClickable(interactionSource = interactionSource) {
            onHaptic()
            clickPulse = true
            val speakText = cell.speakText(isKatakana)
            onSpeak(speakText, cardAudioId)
        },
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val kanaSize = if (kanaText.length > 2) 18.sp else 24.sp

            Text(
                text = kanaText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = kanaSize
                ),
                color = textMain
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = cell.romaji,
                style = MaterialTheme.typography.labelSmall,
                color = textSub
            )
        }
    }
}

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    return noRippleClickable(interactionSource = null, onClick = onClick)
}

private fun Modifier.noRippleClickable(
    interactionSource: MutableInteractionSource?,
    onClick: () -> Unit
): Modifier {
    return composed {
        val source = interactionSource ?: remember { MutableInteractionSource() }
        this.clickable(
            interactionSource = source,
            indication = null,
            onClick = onClick
        )
    }
}

private fun KanaCell.speakText(isKatakana: Boolean): String {
    val text = if (isKatakana) katakana ?: hiragana else hiragana
    return when (text) {
        "っ+k", "ッ+k" -> if (isKatakana) "ガッコウ" else "がっこう"
        "っ+s", "ッ+s" -> if (isKatakana) "カッサ" else "かっさ"
        "っ+t", "ッ+t" -> if (isKatakana) "キッテ" else "きって"
        "っ+p", "ッ+p" -> if (isKatakana) "カップ" else "かっぷ"
        else -> text
    }
}

private val seionData = listOf(
    KanaCell("あ", "ア", "a"), KanaCell("い", "イ", "i"), KanaCell("う", "ウ", "u"), KanaCell("え", "エ", "e"), KanaCell("お", "オ", "o"),
    KanaCell("か", "カ", "ka"), KanaCell("き", "キ", "ki"), KanaCell("く", "ク", "ku"), KanaCell("け", "ケ", "ke"), KanaCell("こ", "コ", "ko"),
    KanaCell("さ", "サ", "sa"), KanaCell("し", "シ", "shi"), KanaCell("す", "ス", "su"), KanaCell("せ", "セ", "se"), KanaCell("そ", "ソ", "so"),
    KanaCell("た", "タ", "ta"), KanaCell("ち", "チ", "chi"), KanaCell("つ", "ツ", "tsu"), KanaCell("て", "テ", "te"), KanaCell("と", "ト", "to"),
    KanaCell("な", "ナ", "na"), KanaCell("に", "ニ", "ni"), KanaCell("ぬ", "ヌ", "nu"), KanaCell("ね", "ネ", "ne"), KanaCell("の", "ノ", "no"),
    KanaCell("は", "ハ", "ha"), KanaCell("ひ", "ヒ", "hi"), KanaCell("ふ", "フ", "fu"), KanaCell("へ", "ヘ", "he"), KanaCell("ほ", "ホ", "ho"),
    KanaCell("ま", "マ", "ma"), KanaCell("み", "ミ", "mi"), KanaCell("む", "ム", "mu"), KanaCell("め", "メ", "me"), KanaCell("も", "モ", "mo"),
    KanaCell("や", "ヤ", "ya"), null, KanaCell("ゆ", "ユ", "yu"), null, KanaCell("よ", "ヨ", "yo"),
    KanaCell("ら", "ラ", "ra"), KanaCell("り", "リ", "ri"), KanaCell("る", "ル", "ru"), KanaCell("れ", "レ", "re"), KanaCell("ろ", "ロ", "ro"),
    KanaCell("わ", "ワ", "wa"), null, null, null, KanaCell("を", "ヲ", "wo"),
    KanaCell("ん", "ン", "n"), null, null, null, null
)

private val dakuonData = listOf(
    KanaCell("が", "ガ", "ga"), KanaCell("ぎ", "ギ", "gi"), KanaCell("ぐ", "グ", "gu"), KanaCell("げ", "ゲ", "ge"), KanaCell("ご", "ゴ", "go"),
    KanaCell("ざ", "ザ", "za"), KanaCell("じ", "ジ", "ji"), KanaCell("ず", "ズ", "zu"), KanaCell("ぜ", "ゼ", "ze"), KanaCell("ぞ", "ゾ", "zo"),
    KanaCell("だ", "ダ", "da"), KanaCell("ぢ", "ヂ", "ji"), KanaCell("づ", "ヅ", "zu"), KanaCell("で", "デ", "de"), KanaCell("ど", "ド", "do"),
    KanaCell("ば", "バ", "ba"), KanaCell("び", "ビ", "bi"), KanaCell("ぶ", "ブ", "bu"), KanaCell("べ", "ベ", "be"), KanaCell("ぼ", "ボ", "bo"),
    KanaCell("ぱ", "パ", "pa"), KanaCell("ぴ", "ピ", "pi"), KanaCell("ぷ", "プ", "pu"), KanaCell("ぺ", "ペ", "pe"), KanaCell("ぽ", "ポ", "po")
)

private val yoonData = listOf(
    KanaCell("きゃ", "キャ", "kya"), KanaCell("きゅ", "キュ", "kyu"), KanaCell("きょ", "キョ", "kyo"),
    KanaCell("ぎゃ", "ギャ", "gya"), KanaCell("ぎゅ", "ギュ", "gyu"), KanaCell("ぎょ", "ギョ", "gyo"),
    KanaCell("しゃ", "シャ", "sha"), KanaCell("しゅ", "シュ", "shu"), KanaCell("しょ", "ショ", "sho"),
    KanaCell("じゃ", "ジャ", "ja"), KanaCell("じゅ", "ジュ", "ju"), KanaCell("じょ", "ジョ", "jo"),
    KanaCell("ちゃ", "チャ", "cha"), KanaCell("ちゅ", "チュ", "chu"), KanaCell("ちょ", "チョ", "cho"),
    KanaCell("にゃ", "ニャ", "nya"), KanaCell("にゅ", "ニュ", "nyu"), KanaCell("にょ", "ニョ", "nyo"),
    KanaCell("ひゃ", "ヒャ", "hya"), KanaCell("ひゅ", "ヒュ", "hyu"), KanaCell("ひょ", "ヒョ", "hyo"),
    KanaCell("びゃ", "ビャ", "bya"), KanaCell("びゅ", "ビュ", "byu"), KanaCell("びょ", "ビョ", "byo"),
    KanaCell("ぴゃ", "ピャ", "pya"), KanaCell("ぴゅ", "ピュ", "pyu"), KanaCell("ぴょ", "ピョ", "pyo"),
    KanaCell("みゃ", "ミャ", "mya"), KanaCell("みゅ", "ミュ", "myu"), KanaCell("みょ", "ミョ", "myo"),
    KanaCell("りゃ", "リャ", "rya"), KanaCell("りゅ", "リュ", "ryu"), KanaCell("りょ", "リョ", "ryo")
)

private val sokuonData = listOf(
    KanaCell("っ+k", "ッ+k", "kk"), KanaCell("っ+s", "ッ+s", "ss"), KanaCell("っ+t", "ッ+t", "tt"), KanaCell("っ+p", "ッ+p", "pp")
)

private val chouonData = listOf(
    KanaCell("ああ", "アー", "aa"), KanaCell("いい", "イー", "ii"), KanaCell("うう", "ウー", "uu"), KanaCell("ええ", "エー", "ee"), KanaCell("おお", "オー", "oo"),
    null, null, null, KanaCell("えい", null, "ei"), KanaCell("おう", null, "ou")
)
