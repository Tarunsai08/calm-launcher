package com.calmlauncher.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calmlauncher.BuildConfig
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmPage
import com.calmlauncher.core.designsystem.CalmTextButton
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.EmptyState
import com.calmlauncher.core.designsystem.SectionHeader
import com.calmlauncher.core.designsystem.SettingRow
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.LaunchLogEntity
import com.calmlauncher.data.tools.LaunchLogRepository
import com.calmlauncher.data.usage.UsageRepository
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class InsightsRange { DAY, WEEK }

data class DayBar(val label: String, val totalMs: Long)

data class InsightsState(
    val loading: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val range: InsightsRange = InsightsRange.DAY,
    val totalMs: Long = 0,
    val unlocks: Int? = null,
    val topApps: List<Pair<String, Long>> = emptyList(),
    val days: List<DayBar> = emptyList(),
    val pauseContinued: Int = 0,
    val choseNotToOpen: Int = 0,
    val extensions: Int = 0,
    val notes: List<LaunchLogEntity> = emptyList(),
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val usage: UsageRepository,
    private val log: LaunchLogRepository,
    private val apps: AppsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    fun load(range: InsightsRange = _state.value.range) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, range = range)
            val hasAccess = usage.hasPermission()
            val today = LocalDate.now()
            val dates = if (range == InsightsRange.DAY) listOf(today) else (6 downTo 0).map { today.minusDays(it.toLong()) }
            val summaries = if (hasAccess) dates.map { it to usage.summarize(it) } else emptyList()
            val merged = HashMap<String, Long>()
            summaries.forEach { (_, s) -> s.perPackageMs.forEach { (pkg, ms) -> merged[pkg] = (merged[pkg] ?: 0L) + ms } }
            // Launchers (including this one) aren't "screen time" in a meaningful sense.
            val excluded = setOf(BuildConfig.APPLICATION_ID)
            val top = merged.entries.filter { it.key !in excluded && it.value >= 60_000 }
                .sortedByDescending { it.value }.take(8).map { apps.labelFor(it.key) to it.value }
            val zone = ZoneId.systemDefault()
            val from = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val outcomes = log.outcomeCounts(from, to)
            _state.value = InsightsState(
                loading = false,
                hasUsageAccess = hasAccess,
                range = range,
                totalMs = merged.filterKeys { it !in excluded }.values.sum(),
                unlocks = summaries.mapNotNull { it.second.unlocks }.takeIf { it.isNotEmpty() }?.sum(),
                topApps = top,
                days = summaries.map { (date, s) ->
                    DayBar(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), s.perPackageMs.filterKeys { it !in excluded }.values.sum())
                },
                pauseContinued = outcomes[LaunchLogEntity.PAUSE_CONTINUED] ?: 0,
                choseNotToOpen = (outcomes[LaunchLogEntity.PAUSE_CANCELLED] ?: 0) + (outcomes[LaunchLogEntity.BLOCK_CLOSED] ?: 0),
                extensions = (outcomes[LaunchLogEntity.BLOCK_EXTENDED] ?: 0) + (outcomes[LaunchLogEntity.BLOCK_OVERRIDDEN] ?: 0),
                notes = log.recentNotes(10),
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: InsightsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    CalmPage(title = stringResource(R.string.insights_title), onBack = onBack) {
        Row(Modifier.padding(horizontal = Spacing.xs)) {
            CalmTextButton(
                stringResource(R.string.insights_today),
                emphasized = state.range == InsightsRange.DAY,
                onClick = { vm.load(InsightsRange.DAY) },
            )
            CalmTextButton(
                stringResource(R.string.insights_week),
                emphasized = state.range == InsightsRange.WEEK,
                onClick = { vm.load(InsightsRange.WEEK) },
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            if (!state.hasUsageAccess) {
                item {
                    SettingRow(
                        title = stringResource(R.string.insights_need_access),
                        description = stringResource(R.string.insights_need_access_desc),
                        onClick = { navigate(Routes.explain(ExplainKind.USAGE)) },
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Text(formatDuration(state.totalMs), style = CalmTheme.type.clock.copy(fontSize = CalmTheme.type.title.fontSize * 2), color = CalmTheme.colors.text)
                        Text(
                            stringResource(if (state.range == InsightsRange.DAY) R.string.insights_screen_time_today else R.string.insights_screen_time_week),
                            style = CalmTheme.type.caption,
                            color = CalmTheme.colors.textSecondary,
                        )
                        state.unlocks?.let {
                            Text(
                                stringResource(R.string.insights_unlocks, it),
                                style = CalmTheme.type.body,
                                color = CalmTheme.colors.text,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
                if (state.range == InsightsRange.WEEK && state.days.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.insights_by_day))
                        val max = state.days.maxOf { it.totalMs }.coerceAtLeast(1)
                        for (d in state.days) BarRow(d.label, formatDuration(d.totalMs), d.totalMs.toFloat() / max)
                    }
                }
                item { SectionHeader(stringResource(R.string.insights_top_apps)) }
                if (state.topApps.isEmpty() && !state.loading) item { EmptyState(stringResource(R.string.insights_no_usage)) }
                val max = state.topApps.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                items(state.topApps) { (label, ms) ->
                    BarRow(label, formatDuration(ms), ms.toFloat() / max)
                }
            }
            item {
                SectionHeader(stringResource(R.string.insights_intentional))
                StatRow(stringResource(R.string.insights_chose_not), state.choseNotToOpen.toString())
                StatRow(stringResource(R.string.insights_through_pause), state.pauseContinued.toString())
                StatRow(stringResource(R.string.insights_extensions), state.extensions.toString())
            }
            if (state.notes.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.insights_your_notes)) }
                items(state.notes, key = { it.id }) { n ->
                    SettingRow(title = n.note.orEmpty(), description = listOfNotNull(n.reason, n.packageName).joinToString(" · "))
                }
            }
            item {
                Text(
                    stringResource(R.string.insights_privacy),
                    style = CalmTheme.type.caption,
                    color = CalmTheme.colors.textSecondary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = CalmTheme.type.body, color = CalmTheme.colors.text, modifier = Modifier.weight(1f))
            Text(value, style = CalmTheme.type.label, color = CalmTheme.colors.textSecondary)
        }
        Box(
            Modifier
                .padding(top = Spacing.xxs)
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .background(CalmTheme.colors.accent, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CalmTheme.type.body, color = CalmTheme.colors.text, modifier = Modifier.weight(1f))
        Text(value, style = CalmTheme.type.title, color = CalmTheme.colors.text)
    }
}
