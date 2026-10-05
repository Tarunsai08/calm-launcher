package com.calmlauncher.feature.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.core.designsystem.Spacing
import com.calmlauncher.data.settings.ClockFormat
import com.calmlauncher.data.settings.HorizontalAlign
import com.calmlauncher.data.settings.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun HorizontalAlign.toAlignment(): Alignment.Horizontal = when (this) {
    HorizontalAlign.START -> Alignment.Start
    HorizontalAlign.CENTER -> Alignment.CenterHorizontally
    HorizontalAlign.END -> Alignment.End
}

fun HorizontalAlign.toTextAlign(): TextAlign = when (this) {
    HorizontalAlign.START -> TextAlign.Start
    HorizontalAlign.CENTER -> TextAlign.Center
    HorizontalAlign.END -> TextAlign.End
}

/** Wall-clock time that ticks on the minute (or second) boundary. */
@Composable
fun rememberNow(withSeconds: Boolean): LocalDateTime {
    val now by produceState(LocalDateTime.now(), withSeconds) {
        while (isActive) {
            value = LocalDateTime.now()
            val ms = System.currentTimeMillis()
            val period = if (withSeconds) 1_000L else 60_000L
            delay(period - ms % period + 5)
        }
    }
    return now
}

@Composable
fun ClockAndDate(
    settings: LauncherSettings,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = rememberNow(settings.showSeconds)
    val use24 = when (settings.clockFormat) {
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
    }
    val locale = Locale.getDefault()
    val timePattern = buildString {
        append(if (use24) "HH:mm" else "h:mm")
        if (settings.showSeconds) append(":ss")
    }
    val timeFormatter = remember(timePattern, locale) { DateTimeFormatter.ofPattern(timePattern, locale) }
    val ampmFormatter = remember(locale) { DateTimeFormatter.ofPattern("a", locale) }
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    val align = settings.homeAlignment
    Column(modifier.fillMaxWidth(), horizontalAlignment = align.toAlignment()) {
        if (settings.showClock) {
            val time = now.format(timeFormatter)
            val suffix = if (use24) "" else " " + now.format(ampmFormatter)
            Text(
                text = time,
                style = CalmTheme.type.clock,
                color = CalmTheme.colors.text,
                textAlign = align.toTextAlign(),
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_open_clock), onClick = onClockClick)
                    .semantics { contentDescription = time + suffix },
            )
            if (!use24) {
                Text(suffix.trim(), style = CalmTheme.type.caption, color = CalmTheme.colors.textSecondary)
            }
        }
        if (settings.showDate) {
            Text(
                text = now.format(dateFormatter),
                style = CalmTheme.type.date,
                color = CalmTheme.colors.textSecondary,
                textAlign = align.toTextAlign(),
                modifier = Modifier
                    .padding(top = Spacing.xxs)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.a11y_open_calendar), onClick = onDateClick),
            )
        }
    }
}

@Composable
fun BatteryLine(align: HorizontalAlign, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = if (l >= 0 && s > 0) l * 100 / s else -1
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    if (level >= 0) {
        Text(
            text = if (charging) stringResource(R.string.battery_charging, level) else stringResource(R.string.battery_level, level),
            style = CalmTheme.type.caption,
            color = CalmTheme.colors.textSecondary,
            textAlign = align.toTextAlign(),
            modifier = modifier,
        )
    }
}

data class NextEvent(val title: String, val begin: Long, val allDay: Boolean)

/** Reads (never writes) the next calendar event in the coming 24 hours. Requires READ_CALENDAR. */
suspend fun loadNextEvent(context: Context): NextEvent? = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        return@withContext null
    }
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, now)
        android.content.ContentUris.appendId(it, now + 24L * 60 * 60 * 1000)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.END,
    )
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0).orEmpty()
                val begin = c.getLong(1)
                val allDay = c.getInt(2) == 1
                val end = c.getLong(3)
                if (end > now && title.isNotBlank()) return@use NextEvent(title, begin, allDay)
            }
            null
        }
    }.getOrNull()
}

@Composable
fun NextEventLine(align: HorizontalAlign, use24: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val event by produceState<NextEvent?>(null, context) {
        while (isActive) {
            value = loadNextEvent(context)
            delay(5 * 60_000L)
        }
    }
    val e = event ?: return
    val time = if (e.allDay) {
        stringResource(R.string.event_all_day)
    } else {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.begin), ZoneId.systemDefault())
        t.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm a", Locale.getDefault()))
    }
    Text(
        text = stringResource(R.string.event_line, time, e.title),
        style = CalmTheme.type.caption,
        color = CalmTheme.colors.textSecondary,
        textAlign = align.toTextAlign(),
        maxLines = 2,
        modifier = modifier,
    )
}
