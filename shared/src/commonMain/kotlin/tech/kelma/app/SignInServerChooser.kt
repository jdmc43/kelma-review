package tech.kelma.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Which KelmaSync server a new sign-in goes to: Kelma's, or one entered by the user. */
@Stable
internal class SignInServerState {
    var custom by mutableStateOf(false)
    var input by mutableStateOf("")

    /** The normalized server for the sign-in, or null while the address is invalid. */
    val endpoint: String?
        get() = if (!custom) {
            DefaultKelmaSyncEndpoint
        } else {
            runCatching { SyncServerAddress.normalize(input) }.getOrNull()
        }

    val error: String?
        get() = if (custom && input.isNotBlank()) {
            runCatching { SyncServerAddress.normalize(input) }.exceptionOrNull()?.message
        } else {
            null
        }

    val isKelma: Boolean get() = endpoint?.let(SyncServerAddress::isDefault) ?: false
}

internal fun savedAccountServer(endpoint: String): String =
    if (SyncServerAddress.isDefault(endpoint)) "Kelma · ${SyncServerAddress.host(endpoint)}" else endpoint

@Composable
internal fun SignInServerChooser(state: SignInServerState, signingIn: Boolean, desktop: Boolean) {
    val secondary = if (desktop) KelmaDesktopColors.TextSecondary else KelmaColors.TextSecondary
    val primary = if (desktop) KelmaDesktopColors.TextPrimary else KelmaColors.TextPrimary
    if (!state.custom) {
        Row(
            modifier = Modifier.fillMaxWidth().testTag("sign-in-server-summary"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sync server", color = secondary, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = savedAccountServer(DefaultKelmaSyncEndpoint),
                    color = primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(
                onClick = { state.custom = true },
                enabled = !signingIn,
                modifier = Modifier.testTag("sign-in-change-server"),
            ) {
                Text("Change", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(if (desktop) 10.dp else 12.dp))
        return
    }
    val endpoint = state.endpoint
    OutlinedTextField(
        value = state.input,
        onValueChange = { state.input = it },
        modifier = Modifier.fillMaxWidth().testTag("sign-in-server"),
        enabled = !signingIn,
        label = { Text("KelmaSync server address") },
        placeholder = { Text("https://sync.example.org") },
        singleLine = true,
        isError = state.error != null,
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.error?.let {
            Text(it, color = KelmaColors.Bad, style = MaterialTheme.typography.bodySmall)
        }
        if (endpoint != null && SyncServerAddress.isUnencryptedRemote(endpoint)) {
            Text(
                text = "http:// is not encrypted: your password and cards would be sent in the clear.",
                color = KelmaColors.Bad,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("sign-in-server-unencrypted"),
            )
        }
        Text(
            text = "This account will sync only with this server. Accounts on a self-hosted " +
                "server are created by whoever runs it.",
            color = secondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    TextButton(
        onClick = {
            state.custom = false
            state.input = ""
        },
        enabled = !signingIn,
        modifier = Modifier.testTag("sign-in-kelma-server"),
    ) {
        Text("Use Kelma's server")
    }
}
