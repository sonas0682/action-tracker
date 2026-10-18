package com.sonas.actiontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonas.actiontracker.sync.PhotoDraft

/** Material "photo_camera" glyph, drawn here so the app doesn't need the large extended icon set. */
val CameraIcon: ImageVector by lazy {
    ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
                "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9z" +
                "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
        ),
        fill = SolidColor(Color.Black)
    ).build()
}

/** Camera icon in the New action header: take a photo or pick one to draft the action from. */
@Composable
fun PhotoButton(onPick: (fromCamera: Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(CameraIcon, contentDescription = "Add from a photo", tint = Fresh.Indigo, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Take photo") }, onClick = { open = false; onPick(true) })
            DropdownMenuItem(text = { Text("Choose photo or screenshot") }, onClick = { open = false; onPick(false) })
        }
    }
}

/** Shown while a photo or shared text is read; everything happens on the phone. */
@Composable
fun PhotoReadingDialog(title: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        icon = { CircularProgressIndicator(Modifier.size(28.dp), color = Fresh.Teal, strokeWidth = 3.dp) },
        title = { Text(title) },
        text = { Text("Finding the text, dates and a summary on this phone.") }
    )
}

/** Shown at the top of the edit sheet for a photo or shared draft: what was read and what drafted it. */
@Composable
fun PhotoNote(draft: PhotoDraft.Draft) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (draft.fromPhoto) CameraIcon else Icons.Rounded.Share, null, tint = Fresh.Teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(if (draft.fromPhoto) "From the photo" else "Shared with Action Tracker", style = MaterialTheme.typography.labelMedium, color = Fresh.Teal)
        }
        Text(
            draft.text.replace(Regex("""\s+"""), " "),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            buildString {
                if (draft.fromPhoto) append("Text read on-device by ML Kit, ")
                append(if (draft.datesByModel) "dates by ML Kit entity extraction" else "dates by rules")
                if (draft.summarizedByNano) append(", title and summary by Gemini Nano")
                append(". Check the fields, then Save.")
            }.replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.bodySmall,
            color = Fresh.TextDim
        )
    }
}
