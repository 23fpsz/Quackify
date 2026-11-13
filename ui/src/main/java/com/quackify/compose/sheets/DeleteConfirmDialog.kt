package com.quackify.compose.sheets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

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
        containerColor = EncoreTheme.colors.cardElevated,
        title = { Text(title, color = EncoreTheme.colors.textPrimary) },
        text = { Text(message, color = EncoreTheme.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel, color = EncoreTheme.colors.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = EncoreTheme.colors.textPrimary)
            }
        }
    )
}
