package tech.kelma.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * Remembers a closable resource and closes exactly that instance when it leaves the composition
 * or is replaced because one of [keys] changed.
 *
 * Each resource owns its own disposal effect so that recreating one client, for example the
 * account-scoped sync client after sign-in, can never close an unrelated long-lived client.
 */
@Composable
internal fun <T : AutoCloseable> rememberClosable(vararg keys: Any?, factory: () -> T): T {
    val resource = remember(*keys) { factory() }
    DisposableEffect(resource) {
        onDispose { resource.close() }
    }
    return resource
}
