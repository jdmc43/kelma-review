package tech.kelma.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ClosableResourcesUiTest {
    private class Resource(val name: String) : AutoCloseable {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    @Test
    fun replacingAKeyedResourceKeepsIndependentLongLivedResourcesOpen() = runComposeUiTest {
        // Mirrors App: the sync client is keyed by the active account, while the account client
        // must remain usable for deletion, registration, and password resets after sign-in.
        var account by mutableStateOf("guest")
        var visible by mutableStateOf(true)
        val syncClients = mutableListOf<Resource>()
        val accountClients = mutableListOf<Resource>()
        setContent {
            if (visible) {
                val syncClient = rememberClosable(account) { Resource("sync-$account") }
                val accountClient = rememberClosable { Resource("account") }
                if (syncClients.lastOrNull() !== syncClient) syncClients += syncClient
                if (accountClients.lastOrNull() !== accountClient) accountClients += accountClient
            }
        }
        waitForIdle()

        account = "signed-in"
        waitForIdle()

        assertEquals(listOf("sync-guest", "sync-signed-in"), syncClients.map { it.name })
        assertTrue(syncClients[0].closed, "the replaced sync client is closed")
        assertFalse(syncClients[1].closed, "the active sync client stays open")
        assertEquals(1, accountClients.size, "the account client is not recreated")
        assertFalse(accountClients.single().closed, "switching accounts must not close the account client")

        visible = false
        waitForIdle()

        assertTrue(syncClients[1].closed)
        assertTrue(accountClients.single().closed, "the account client closes when it leaves composition")
    }
}
