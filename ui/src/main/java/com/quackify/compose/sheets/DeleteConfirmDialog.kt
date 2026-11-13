package com.quackify.compose.sheets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.quackify.compose.theme.EncoreColors

@Composable
internal fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EncoreColors.CardElevated,
        title = { Text(title, color = EncoreColors.TextPrimary) },
        text = { Text(message, color = EncoreColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel, color = EncoreColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = EncoreColors.TextPrimary)
            }
        }
    )
}
