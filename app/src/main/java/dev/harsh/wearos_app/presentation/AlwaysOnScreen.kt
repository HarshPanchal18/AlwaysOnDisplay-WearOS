package dev.harsh.wearos_app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.ambient.AmbientState
import com.google.android.horologist.compose.ambient.AmbientStateUpdate
import com.google.android.horologist.compose.layout.fillMaxRectangle
import dev.harsh.wearos_app.R
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

// Number of pixels to offset the content rendered in the display to prevent screen burn-in.
const val BURN_IN_OFFSET_PX = 10

@Composable
fun AlwaysOnScreen(
    ambientStateUpdate: AmbientStateUpdate,
    drawCount: Int,
    currentInstant: Instant,
    currentTime: LocalTime,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US) }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxRectangle()
            .padding(with(LocalDensity.current) { BURN_IN_OFFSET_PX.toDp() })
            .ambientMode(ambientStateUpdate)
    ) {
        Text(
            text = dateFormat.format(currentTime),
            modifier = Modifier.testTag("time"),
            style = MaterialTheme.typography.title1,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.testTag("timestamp"),
            text = stringResource(id = R.string.timestamp_label, currentInstant.toEpochMilli()),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.testTag("mode"),
            text = stringResource(
                id = when (ambientStateUpdate.ambientState) {
                    AmbientState.Interactive -> R.string.mode_active_label
                    is AmbientState.Ambient -> R.string.mode_ambient_label
                }
            ),
            style = MaterialTheme.typography.body2,
            color = Color.Green,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.testTag("rate"),
            text = stringResource(
                id = R.string.update_rate_label,
                when (ambientStateUpdate.ambientState) {
                    AmbientState.Interactive -> ACTIVE_INTERVAL.seconds
                    is AmbientState.Ambient -> AMBIENT_INTERVAL.seconds
                }
            ),
            style = MaterialTheme.typography.body2,
            color = Color.Green,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.testTag("drawCount"),
            text = stringResource(id = R.string.draw_count_label, drawCount),
            style = MaterialTheme.typography.body2,
            color = Color.Green,
            textAlign = TextAlign.Center
        )
    }
}

private fun Modifier.ambientMode(
    ambientStateUpdate: AmbientStateUpdate,
): Modifier = composed {
    val translationX = rememberBurnInTranslation(ambientStateUpdate)
    val translationY = rememberBurnInTranslation(ambientStateUpdate)

    this
        .graphicsLayer {
            this.translationX = translationX
            this.translationY = translationY
        }
        .ambientGray(ambientStateUpdate.ambientState)
}

@Composable
private fun rememberBurnInTranslation(
    ambientStateUpdate: AmbientStateUpdate,
): Float =
    remember(ambientStateUpdate) {
        when (val state = ambientStateUpdate.ambientState) {
            AmbientState.Interactive -> 0f
            is AmbientState.Ambient -> if (state.ambientDetails?.burnInProtectionRequired == true) {
                Random.nextInt(-BURN_IN_OFFSET_PX, BURN_IN_OFFSET_PX + 1).toFloat()
            } else {
                0f
            }
        }
    }

private val grayscale = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToSaturation(0f)
        }
    )
    isAntiAlias = false
}

internal fun Modifier.ambientGray(ambientState: AmbientState): Modifier =
    if (ambientState is AmbientState.Ambient) {
        graphicsLayer {
            scaleX = 0.9f
            scaleY = 0.9f
        }.drawWithContent {
            drawIntoCanvas {
                it.withSaveLayer(size.toRect(), grayscale) {
                    drawContent()
                }
            }
        }
    } else {
        this
    }
