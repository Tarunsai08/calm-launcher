package com.calmlauncher.feature.gate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.domain.focus.BlockReason
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GateScreen(viewModel: GateViewModel, state: GateState, settings: LauncherSettings) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CalmTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                GateState.Loading, GateState.Done -> Unit
                is GateState.Pause -> PauseContent(viewModel, state, settings)
                is GateState.Block -> BlockContent(viewModel, state)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PauseContent(viewModel: GateViewModel, state: GateState.Pause, settings: LauncherSettings) {
    var remaining by rememberSaveable { mutableIntStateOf(state.seconds) }
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    BackHandler { viewModel.cancelPause(reason, note) }

    val motion = CalmTheme.motion
    if (settings.breathingAnimation && motion.enabled) {
        BreathingCircle()
        Spacer(Modifier.height(Spacing.xl))
    }
    Text(
        stringResource(R.string.pause_title, viewModel.appLabel),
        style = CalmTheme.type.title,
        color = CalmTheme.colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        if (remaining > 0) stringResource(R.string.pause_breathe, remaining) else stringResource(R.string.pause_ready),
        style = CalmTheme.type.body,
        color = CalmTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )

    if (state.askReason) {
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.pause_why), style = CalmTheme.type.label, color = CalmTheme.colors.text)
        Spacer(Modifier.height(Spacing.xs))
        val reasons = listOf(
            stringResource(R.string.reason_specific),
            stringResource(R.string.reason_reply),
            stringResource(R.string.reason_break),
            stringResource(R.string.reason_habit),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (r in reasons) {
                ReasonChip(r, selected = reason == r) { reason = if (reason == r) null else r }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            placeholder = { Text(stringResource(R.string.pause_note_hint)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
    }

    Spacer(Modifier.height(Spacing.xl))
    CalmTextButton(stringResource(R.string.pause_not_now), emphasized = true, onClick = { viewModel.cancelPause(reason, note) })
    CalmTextButton(
        if (remaining > 0) stringResource(R.string.pause_open_in, viewModel.appLabel, remaining) else stringResource(R.string.pause_open, viewModel.appLabel),
        enabled = remaining <= 0,
        onClick = { viewModel.continueAfterPause(reason, note) },
    )
}

@Composable
private fun BreathingCircle() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(
        Modifier
            .size(160.dp)
            .scale(scale)
            .border(1.dp, CalmTheme.colors.textSecondary, CircleShape),
    )
}

@Composable
private fun ReasonChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = CalmTheme.type.label,
        color = if (selected) CalmTheme.colors.onAccent else CalmTheme.colors.text,
        modifier = Modifier
            .heightIn(min = Spacing.touchTarget)
            .background(if (selected) CalmTheme.colors.accent else CalmTheme.colors.surface, RoundedCornerShape(24.dp))
            .border(1.dp, CalmTheme.colors.divider, RoundedCornerShape(24.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
private fun BlockContent(viewModel: GateViewModel, state: GateState.Block) {
    BackHandler { viewModel.closeBlocked() }
    var friction by rememberSaveable { mutableStateOf<String?>(null) }
    val label = viewModel.appLabel

    val (title, body) = when (val r = state.reason) {
        BlockReason.FocusMode -> stringResource(R.string.block_focus_title) to stringResource(R.string.block_focus_body, label)
        is BlockReason.Scheduled -> {
            val until = r.endsAt?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
            stringResource(R.string.block_schedule_title) to
                if (until != null) stringResource(R.string.block_schedule_body_until, label, until) else stringResource(R.string.block_schedule_body, label)
        }
        BlockReason.AlwaysBlocked -> stringResource(R.string.block_always_title) to stringResource(R.string.block_always_body, label)
        is BlockReason.DailyLimit -> stringResource(R.string.block_limit_title) to
            stringResource(R.string.block_limit_body, r.usedMinutes, r.limitMinutes, label)
    }
    Text(
        title,
        style = CalmTheme.type.title,
        color = CalmTheme.colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(body, style = CalmTheme.type.body, color = CalmTheme.colors.textSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.xl))

    CalmTextButton(stringResource(R.string.block_close), emphasized = true, onClick = { viewModel.closeBlocked() })

    when (val r = state.reason) {
        is BlockReason.DailyLimit -> {
            CalmTextButton(stringResource(R.string.block_extend_5), onClick = { viewModel.extendLimit(5, r.isGroup) })
            CalmTextButton(stringResource(R.string.block_extend_day), onClick = { friction = FRICTION_DAY })
        }
        BlockReason.FocusMode -> {
            CalmTextButton(stringResource(R.string.block_end_focus), onClick = {
                if (state.strictFocus) friction = FRICTION_FOCUS else viewModel.endFocus()
            })
        }
        is BlockReason.Scheduled, BlockReason.AlwaysBlocked -> {
            CalmTextButton(stringResource(R.string.block_override, GateViewModel.OVERRIDE_MINUTES), onClick = { friction = FRICTION_OVERRIDE })
        }
    }

    friction?.let { kind ->
        Spacer(Modifier.height(Spacing.lg))
        FrictionChallenge(
            challenge = viewModel.challenge,
            onCancel = { friction = null },
            onPassed = {
                friction = null
                when (kind) {
                    FRICTION_DAY -> viewModel.unlimitedToday((state.reason as? BlockReason.DailyLimit)?.isGroup ?: false)
                    FRICTION_FOCUS -> viewModel.endFocus()
                    else -> viewModel.overrideBlock()
                }
            },
        )
    }
}

/**
 * Deliberate friction: wait a few seconds, then type a short phrase. Enough to interrupt
 * autopilot, never enough to trap anyone.
 */
@Composable
fun FrictionChallenge(challenge: String, onCancel: () -> Unit, onPassed: () -> Unit, waitSeconds: Int = 10) {
    var remaining by rememberSaveable { mutableIntStateOf(waitSeconds) }
    var typed by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.friction_type, challenge),
            style = CalmTheme.type.body,
            color = CalmTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val matches = typed.trim().equals(challenge, ignoreCase = true)
        Spacer(Modifier.height(Spacing.xs))
        CalmTextButton(
            if (remaining > 0) stringResource(R.string.friction_wait, remaining) else stringResource(R.string.friction_confirm),
            enabled = remaining <= 0 && matches,
            emphasized = true,
            onClick = onPassed,
        )
        CalmTextButton(stringResource(R.string.action_cancel), onClick = onCancel)
    }
}

private const val FRICTION_DAY = "day"
private const val FRICTION_FOCUS = "focus"
private const val FRICTION_OVERRIDE = "override"
