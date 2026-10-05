package com.calmlauncher.feature.drawer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTheme

/**
 * Fast-scroll A–Z strip. Touch or drag to jump; the strip excludes itself from the system
 * back gesture (it sits on the edge), which Android allows for small regions like this.
 * TalkBack users get each letter as its own focusable, clickable item instead.
 */
@Composable
fun AlphabetIndex(
    letters: List<Pair<String, Int>>,
    onJump: (index: Int, letter: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableIntStateOf(-1) }
    var heightPx by remember { mutableIntStateOf(1) }
    val currentLetters by rememberUpdatedState(letters)
    val currentOnJump by rememberUpdatedState(onJump)
    val description = stringResource(R.string.a11y_alphabet_index)

    fun select(y: Float) {
        val list = currentLetters
        if (list.isEmpty()) return
        val i = ((y / heightPx.coerceAtLeast(1)) * list.size).toInt().coerceIn(0, list.lastIndex)
        if (i != active) {
            active = i
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnJump(list[i].second, list[i].first)
        }
    }

    Column(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .systemGestureExclusion()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                heightPx = size.height
                detectTapGestures(onPress = { offset ->
                    heightPx = size.height
                    select(offset.y)
                    tryAwaitRelease()
                    active = -1
                })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> heightPx = size.height; select(offset.y) },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        select(change.position.y)
                    },
                )
            }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, (letter, _) ->
            Text(
                text = letter,
                fontSize = 11.sp,
                fontWeight = if (i == active) FontWeight.Bold else FontWeight.Normal,
                color = if (i == active) CalmTheme.colors.accent else CalmTheme.colors.textSecondary,
            )
        }
    }
}
