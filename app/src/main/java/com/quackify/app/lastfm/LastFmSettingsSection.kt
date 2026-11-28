package com.quackify.app.lastfm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import kotlinx.coroutines.launch

@Composable
fun LastFmSettingsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var lastFmSettings by remember {
        mutableStateOf(LastFmSettings.load(context))
    }
    fun updateLastFmSettings(transform: (LastFmSettings) -> LastFmSettings) {
        val updated = transform(lastFmSettings)
        lastFmSettings = updated
        LastFmSettings.save(context, updated)
    }

    val lastFmAuthPrefs = remember {
        context.getSharedPreferences("lastfm_auth_state", Context.MODE_PRIVATE)
    }
    val lastFmAuthScope = rememberCoroutineScope()
    var lastFmConnected by remember {
        mutableStateOf(LastFmSecureStore.getSessionKey(context) != null)
    }
    var lastFmAccountName by remember {
        mutableStateOf(lastFmAuthPrefs.getString("username", "").orEmpty())
    }
    var lastFmPendingToken by remember {
        mutableStateOf(lastFmAuthPrefs.getString("pending_token", "").orEmpty())
    }
    var lastFmAuthBusy by remember { mutableStateOf(false) }
    var lastFmAuthMessage by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Last.fm",
            style = EncoreTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Text(
            text = stringResource(R.string.lastfm_description),
            style = EncoreTheme.typography.bodySmall.copy(
                color = EncoreColors.TextSecondary
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = lastFmSettings.apiKey,
            onValueChange = { value ->
                updateLastFmSettings { it.copy(apiKey = value) }
            },
            label = { Text(stringResource(R.string.lastfm_api_key)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = lastFmSettings.sharedSecret,
            onValueChange = { value ->
                updateLastFmSettings { it.copy(sharedSecret = value) }
            },
            label = { Text(stringResource(R.string.lastfm_shared_secret)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        Text(
            text = if (lastFmConnected) {
                context.getString(R.string.lastfm_connected, lastFmAccountName.ifBlank { "Last.fm" })
            } else {
                stringResource(R.string.lastfm_not_connected)
            },
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White
            ),
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Button(
            enabled = !lastFmAuthBusy &&
                !lastFmConnected &&
                LastFmRuntime.isConfigured(
                    lastFmSettings.apiKey,
                    lastFmSettings.sharedSecret
                ),
            onClick = {
                lastFmAuthScope.launch {
                    lastFmAuthBusy = true
                    lastFmAuthMessage = ""
                    try {
                        if (lastFmPendingToken.isBlank()) {
                            val result = LastFmRuntime
                                .client(lastFmSettings.apiKey, lastFmSettings.sharedSecret)
                                .call("auth.getToken")
                            val token = result.optString("token")
                            check(token.isNotBlank()) {
                                context.getString(R.string.lastfm_token_missing)
                            }

                            lastFmPendingToken = token
                            lastFmAuthPrefs.edit()
                                .putString("pending_token", token)
                                .apply()

                            val url = LastFmRuntime
                                .authorizationUrl(token, lastFmSettings.apiKey)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                            lastFmAuthMessage = context.getString(
                                R.string.lastfm_browser_authorization_instructions
                            )
                        } else {
                            val session = LastFmRuntime
                                .client(lastFmSettings.apiKey, lastFmSettings.sharedSecret)
                                .call(
                                    "auth.getSession",
                                    mapOf("token" to lastFmPendingToken)
                                )
                            val sessionNode = session.optJSONObject("session")
                            val key = sessionNode?.optString("key").orEmpty()
                            val name = sessionNode?.optString("name").orEmpty()

                            check(key.isNotBlank()) {
                                context.getString(R.string.lastfm_auth_failed)
                            }

                            LastFmSecureStore.saveSessionKey(context, key)
                            lastFmAuthPrefs.edit()
                                .putString("username", name)
                                .remove("pending_token")
                                .apply()

                            lastFmConnected = true
                            lastFmAccountName = name
                            lastFmPendingToken = ""
                            updateLastFmSettings {
                                it.copy(scrobblingEnabled = true)
                            }
                            lastFmAuthMessage = context.getString(R.string.lastfm_auth_success)
                        }
                    } catch (e: Exception) {
                        lastFmAuthMessage = e.message
                            ?: context.getString(R.string.lastfm_auth_failed)
                    } finally {
                        lastFmAuthBusy = false
                    }
                }
            },
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                when {
                    lastFmAuthBusy -> stringResource(R.string.lastfm_connecting)
                    lastFmPendingToken.isNotBlank() -> stringResource(R.string.lastfm_finish_connection)
                    else -> stringResource(R.string.lastfm_connect)
                }
            )
        }

        if (!LastFmRuntime.isConfigured(
                lastFmSettings.apiKey,
                lastFmSettings.sharedSecret
            )
        ) {
            Text(
                text = stringResource(R.string.lastfm_api_credentials_unavailable),
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (lastFmConnected) {
            TextButton(
                onClick = {
                    LastFmSecureStore.clearSessionKey(context)
                    lastFmAuthPrefs.edit().clear().apply()
                    lastFmConnected = false
                    lastFmAccountName = ""
                    lastFmPendingToken = ""
                    updateLastFmSettings {
                        it.copy(
                            scrobblingEnabled = false,
                            syncLikesEnabled = false
                        )
                    }
                    lastFmAuthMessage = context.getString(R.string.lastfm_disconnected_message)
                }
            ) {
                Text(stringResource(R.string.lastfm_disconnect))
            }
        }

        if (lastFmAuthMessage.isNotBlank()) {
            Text(
                text = lastFmAuthMessage,
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                ),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Toggles
        LastFmToggleItem(
            title = stringResource(R.string.lastfm_scrobbling),
            subtitle = stringResource(R.string.lastfm_scrobbling_description),
            isChecked = lastFmSettings.scrobblingEnabled,
            enabled = lastFmConnected,
            onCheckedChange = { enabled ->
                updateLastFmSettings { it.copy(scrobblingEnabled = enabled) }
            }
        )

        LastFmToggleItem(
            title = stringResource(R.string.lastfm_now_playing),
            subtitle = stringResource(R.string.lastfm_now_playing_description),
            isChecked = lastFmSettings.nowPlayingEnabled,
            enabled = lastFmSettings.scrobblingEnabled,
            onCheckedChange = { enabled ->
                updateLastFmSettings { it.copy(nowPlayingEnabled = enabled) }
            }
        )

        LastFmToggleItem(
            title = stringResource(R.string.lastfm_sync_likes),
            subtitle = stringResource(R.string.lastfm_sync_likes_description),
            isChecked = lastFmSettings.syncLikesEnabled,
            enabled = lastFmConnected,
            onCheckedChange = { enabled ->
                updateLastFmSettings { it.copy(syncLikesEnabled = enabled) }
            }
        )

        // Threshold Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lastfm_scrobble_threshold),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "${lastFmSettings.thresholdPercent}%",
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary
                    )
                )
            }
            Text(
                text = stringResource(R.string.lastfm_scrobble_threshold_description),
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary
                ),
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
            Slider(
                value = lastFmSettings.thresholdPercent.toFloat(),
                onValueChange = { percent ->
                    updateLastFmSettings {
                        it.copy(thresholdPercent = percent.toInt().coerceIn(1, 100))
                    }
                },
                valueRange = 1f..100f
            )
        }
    }
}

@Composable
private fun LastFmToggleItem(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else EncoreColors.TextSecondary
                )
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1DB954),
                uncheckedThumbColor = EncoreColors.TextSecondary,
                uncheckedTrackColor = Color(0xFF282828)
            )
        )
    }
}
