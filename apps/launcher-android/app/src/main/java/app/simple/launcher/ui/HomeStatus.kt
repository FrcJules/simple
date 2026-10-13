package app.simple.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** What the home screen shows about the phone itself. [batteryPercent] is null when unknown. */
data class HomeStatus(
    val time: String,
    val date: String,
    val batteryPercent: Int?,
    val charging: Boolean,
) {
    val batteryLow: Boolean get() = batteryPercent != null && batteryPercent <= LOW_PERCENT

    companion object {
        const val LOW_PERCENT = 20

        fun of(now: LocalDateTime, batteryPercent: Int?, charging: Boolean, locale: Locale = Locale.getDefault()) = HomeStatus(
            time = now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
            date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
            batteryPercent = batteryPercent?.coerceIn(0, 100),
            charging = charging,
        )

        /** Battery percentage from the sticky battery broadcast, or null when level/scale are missing. */
        fun percentOf(level: Int, scale: Int): Int? = if (level < 0 || scale <= 0) null else (level * 100 / scale).coerceIn(0, 100)
    }
}

/**
 * Counts taps that follow each other closely; reaching [needed] taps within [windowMs] of the first one
 * returns true once and starts over. Used for the family's hidden way into the settings.
 */
class SecretTapCounter(private val needed: Int = 7, private val windowMs: Long = 4_000) {
    private var count = 0
    private var first = 0L

    fun tap(nowMs: Long): Boolean {
        if (count == 0 || nowMs - first > windowMs) {
            count = 0
            first = nowMs
        }
        count++
        if (count >= needed) {
            count = 0
            return true
        }
        return false
    }
}

/** Live time and battery. Needs no permission: the battery broadcast is sticky and public. */
@Composable
fun rememberHomeStatus(): HomeStatus {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(readBattery(context, context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                battery = readBattery(c, intent)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    return HomeStatus.of(now, battery.first, battery.second)
}

private fun readBattery(@Suppress("UNUSED_PARAMETER") c: Context, i: Intent?): Pair<Int?, Boolean> {
    if (i == null) return null to false
    val pct = HomeStatus.percentOf(i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), i.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return pct to (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
}
