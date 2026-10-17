package com.sonas.actiontracker.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonas.actiontracker.sync.VoiceDraft

/** Material "mic" glyph, drawn here so the app doesn't need the large extended icon set. */
val MicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
                "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28" +
                "c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}

/**
 * Listen mode: records one spoken sentence with Android speech recognition
 * (on-device when the phone offers it), shows the words live, and hands the
 * final text to [onHeard]. [drafting] shows a spinner while the task is drafted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenSheet(drafting: Boolean, onHeard: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var heard by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }
    var onDevice by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(attempt, onDevice) {
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "This phone has no speech recognition service."
            return@DisposableEffect onDispose { }
        }
        val rec = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        var finished = false
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; error = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }?.let { heard = it }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() } ?: heard
                if (finished) return
                finished = true
                if (text.isBlank()) error = "Didn't catch that. Tap the mic to try again."
                else { heard = text; onHeard(text) }
            }

            override fun onError(code: Int) {
                listening = false
                if (finished) return
                // The on-device recognizer may lack the language pack: fall back to the regular one.
                if (onDevice && (code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE ||
                        code == SpeechRecognizer.ERROR_CLIENT || code == SpeechRecognizer.ERROR_SERVER)
                ) {
                    onDevice = false
                    return
                }
                finished = true
                if (heard.isNotBlank() && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    onHeard(heard)
                    return
                }
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Didn't catch that. Tap the mic to try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition needs a connection on this phone. Try again online."
                    else -> "Speech recognition stopped (error $code). Tap the mic to try again."
                }
            }
        })
        heard = ""
        rec.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
        onDispose {
            recognizer = null
            rec.destroy()
        }
    }

    val pulse = rememberInfiniteTransition(label = "listen")
    val halo by pulse.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "halo")
    val voice by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, tween(120), label = "level")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                when {
                    drafting -> "Drafting your task"
                    error != null -> "Not heard"
                    listening -> "Listening"
                    else -> "Getting ready"
                },
                style = MaterialTheme.typography.headlineSmall.copy(brush = Fresh.accentHorizontal)
            )
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                if (listening) {
                    Box(Modifier.size(96.dp).scale(halo * voice).clip(CircleShape).background(Fresh.Teal.copy(alpha = 0.25f)))
                }
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(Fresh.accent)
                        .clickable(enabled = !drafting) {
                            if (listening) recognizer?.stopListening()
                            else { error = null; attempt++ }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (drafting) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    else Icon(MicIcon, contentDescription = if (listening) "Stop listening" else "Listen again", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).glass(RoundedCornerShape(16.dp)).padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    error ?: heard.ifBlank { "Say the task, for example \"Sign Vibu's permission slip by Friday\"" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (error != null) Fresh.Coral else if (heard.isBlank()) Fresh.TextDim else Fresh.Text,
                    textAlign = TextAlign.Center
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Fresh.TextDim) }
                Spacer(Modifier.weight(1f))
                if (listening && !drafting) {
                    TextButton(onClick = { recognizer?.stopListening() }) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Done")
                    }
                }
            }
        }
    }
}

/** Shown at the top of the edit sheet for a voice draft: what was heard and what drafted it. */
@Composable
fun VoiceNote(draft: VoiceDraft.Draft) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MicIcon, null, tint = Fresh.Teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("You said", style = MaterialTheme.typography.labelMedium, color = Fresh.Teal)
        }
        Text("“${draft.transcript}”", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (draft.tidiedByNano) "Date, person and context picked by rules; title tidied on-device by Gemini Nano."
            else "Date, person and context picked by rules on this phone. Check the fields, then Save.",
            style = MaterialTheme.typography.bodySmall,
            color = Fresh.TextDim
        )
    }
}

// SpeechRecognizer constants added in API 31; minSdk is 30.
private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
private const val ERROR_LANGUAGE_UNAVAILABLE = 13
