package com.jamalsquad.jamalify.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Dialog nama playlist baru. Kolomnya langsung fokus dan tombol Selesai di
 * keyboard ikut membuat playlist, supaya tidak perlu mengetuk dua kali.
 */
@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "Playlist baru",
    initialName: String = "",
    confirmLabel: String = "Buat"
) {
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nama playlist") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (name.text.isNotBlank()) onConfirm(name.text.trim()) }),
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.text.trim()) },
                enabled = name.text.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
