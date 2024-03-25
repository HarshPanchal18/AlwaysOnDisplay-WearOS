package dev.harsh.wearos_app.presentation

import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material.MaterialTheme
import com.google.android.horologist.compose.ambient.AmbientState
import com.google.android.horologist.compose.ambient.AmbientStateUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

// Duration between updates while in active mode.
val ACTIVE_INTERVAL: Duration = Duration.ofSeconds(1)

// Duration between updates while in ambient mode.
val AMBIENT_INTERVAL: Duration = Duration.ofSeconds(1)

const val AMBIENT_UPDATE_ACTION = "com.example.android.wearable.wear.alwayson.action.AMBIENT_UPDATE"

const val TAG = "AlwaysOnApp"

// Create a PendingIntent which we'll give to the AlarmManager to send ambient mode updates on an interval which we've define.
private val ambientUpdateIntent = Intent(AMBIENT_UPDATE_ACTION)

@Composable
fun AlwaysOnApp(
    clock: Clock,
    activeDispatcher: CoroutineDispatcher,
    ambientStateUpdate: AmbientStateUpdate,
) {
    val ambientUpdateAlarmManager = rememberAlarmManager()
    val context = LocalContext.current
    val ambientUpdatePendingIntent = remember(context) {
        PendingIntent.getBroadcast(
            context,
            0,
            ambientUpdateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    // A ping used to set up a loopback side-effect loop, to continuously update the time.
    var updateDataTrigger by remember { mutableLongStateOf(0L) }
    var currentInstant by remember { mutableStateOf(Instant.now(clock)) } // current instant to display
    var currentTime by remember { mutableStateOf(LocalTime.now(clock)) } // Current time to display
    var drawCount by remember { mutableIntStateOf(0) } // The number of times the current time and instant have been updated

    fun updateData() {
        updateDataTrigger++
        currentInstant = Instant.now(clock)
        currentTime = LocalTime.now(clock)
        drawCount++
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val isResumed by produceState(initialValue = false) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            value = true
            try {
                awaitCancellation()
            } finally {
                value = false
            }
        }
    }

    if (isResumed) {
        when (ambientStateUpdate.ambientState) {
            is AmbientState.Ambient -> {
                // Setup the broadcast receiver
                SystemBroadcastReceiver(systemAction = AMBIENT_UPDATE_ACTION) { updateData() }
                DisposableEffect(ambientUpdateAlarmManager, ambientUpdatePendingIntent) {
                    onDispose {
                        // Cancel any ongoing pending intent
                        ambientUpdateAlarmManager.cancel(ambientUpdatePendingIntent)
                    }
                }
            }

            AmbientState.Interactive -> Unit
        }

        // Whenever we change ambient state (and initially) update the data.
        LaunchedEffect(ambientStateUpdate.ambientState) {
            updateData()
        }

        // Then setup a ping to refresh again
        LaunchedEffect(updateDataTrigger, ambientStateUpdate.ambientState) {
            when (ambientStateUpdate.ambientState) {
                is AmbientState.Ambient -> {
                    val triggerTime = currentInstant.getNextInstantWithInterval(AMBIENT_INTERVAL)
                    try {
                        ambientUpdateAlarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime.toEpochMilli(),
                            ambientUpdatePendingIntent
                        )
                    } catch (e: SecurityException) {
                        Log.d(
                            TAG,
                            arrayOf(
                                "SecurityException when calling setExact(),",
                                "screen will not be refreshed"
                            ).joinToString(" ")
                        )
                    }
                }

                AmbientState.Interactive -> {
                    val delay = currentInstant.getDelayToNextInstantWithInterval(AMBIENT_INTERVAL)
                    withContext(activeDispatcher) {
                        delay(delay.toMillis())
                    }
                    updateData()
                }
            }
        }
    }

    MaterialTheme {
        AlwaysOnScreen(
            ambientStateUpdate = ambientStateUpdate,
            drawCount = drawCount,
            currentInstant = currentInstant,
            currentTime = currentTime
        )
    }
}

/**
 * Returns the delay from this [Instant] to the next one that is aligned with the given [interval].
 */
private fun Instant.getDelayToNextInstantWithInterval(interval: Duration): Duration =
    Duration.ofMillis(interval.toMillis() - toEpochMilli() % interval.toMillis())

/**
 * Returns the next [Instant] that is aligned with the given [interval].
 */
private fun Instant.getNextInstantWithInterval(interval: Duration): Instant =
    plus(getDelayToNextInstantWithInterval(interval))


@Composable
fun SystemBroadcastReceiver(systemAction: String, onSystemEvent: (intent: Intent?) -> Unit) {
    val context = LocalContext.current

    val currentOnSystemEvent by rememberUpdatedState(newValue = onSystemEvent) // Safely use the latest onSystemEvent lambda passed to the function

    // If either context or systemAction changes, unregister and register again
    DisposableEffect(context, systemAction) {
        val intentFilter = IntentFilter(systemAction)
        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                currentOnSystemEvent(intent)
            }
        }

        registerReceiver(context, broadcast, intentFilter, RECEIVER_NOT_EXPORTED)

        // When the effect leaves the Composition, remove the callback
        onDispose {
            context.unregisterReceiver(broadcast)
        }
    }
}

@Composable
fun rememberAlarmManager(): AlarmManager {
    val context = LocalContext.current
    return remember(context) {
        context.getSystemService()!!
    }
}
