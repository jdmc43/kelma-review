package tech.kelma.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncServerAddressTest {
    @Test
    fun addressesAreCanonical() {
        val cases = mapOf(
            "sync.example.org" to "https://sync.example.org",
            " HTTPS://Sync.Example.org/ " to "https://sync.example.org",
            "https://sync.example.org:443" to "https://sync.example.org",
            "http://192.168.1.20:8080/" to "http://192.168.1.20:8080",
            "https://example.org/kelma/" to "https://example.org/kelma",
            "https://sync2.ankiai.tech" to DefaultKelmaSyncEndpoint,
        )
        cases.forEach { (raw, expected) -> assertEquals(expected, SyncServerAddress.normalize(raw), raw) }
    }

    @Test
    fun badAddressesAreRejectedWithAReason() {
        listOf("", "ftp://example.org", "https://", "https://example.org/?a=1", "https://u:p@example.org",
            "https://example.org:99999", "https://exa mple.org").forEach { raw ->
            val failure = assertFailsWith<IllegalArgumentException>(raw) { SyncServerAddress.normalize(raw) }
            assertTrue(failure.message.orEmpty().isNotBlank(), raw)
        }
    }

    @Test
    fun labelsAndTransportSafety() {
        assertTrue(SyncServerAddress.isDefault("sync2.kelma.tech/"))
        assertEquals("Kelma", SyncServerAddress.label(DefaultKelmaSyncEndpoint))
        assertEquals("sync.example.org:8443", SyncServerAddress.label("https://sync.example.org:8443"))
        assertTrue(SyncServerAddress.isUnencryptedRemote("http://sync.example.org"))
        assertFalse(SyncServerAddress.isUnencryptedRemote("http://localhost:8080"))
        assertFalse(SyncServerAddress.isUnencryptedRemote("http://10.0.0.5"))
        assertFalse(SyncServerAddress.isUnencryptedRemote("http://172.20.1.1"))
        assertFalse(SyncServerAddress.isUnencryptedRemote("https://sync.example.org"))
    }

    @Test
    fun phonesNeedEncryptedServers() {
        val phone = SignInServerState(desktop = false).apply {
            custom = true
            input = "http://192.168.1.20:8080"
        }
        assertEquals(null, phone.endpoint)
        assertTrue(phone.error.orEmpty().contains("https://"))
        phone.input = "sync.home.example"
        assertEquals("https://sync.home.example", phone.endpoint)
        assertEquals(null, phone.error)

        val desktop = SignInServerState(desktop = true).apply {
            custom = true
            input = "http://192.168.1.20:8080"
        }
        assertEquals("http://192.168.1.20:8080", desktop.endpoint)
        assertEquals(null, desktop.error)
    }

    @Test
    fun theSameUsernameOnTwoServersIsTwoAccounts() {
        val registry = LocalAccountRegistry(InMemoryRegistry())
        val kelma = registry.activate(DefaultKelmaSyncEndpoint, "maria@example.org")
        val home = registry.activate(SyncServerAddress.normalize("sync.home.example/"), "maria@example.org")
        kotlin.test.assertNotEquals(kelma, home)
        assertEquals("https://sync.home.example", registry.activeAccount()?.endpoint)
        assertEquals(kelma, registry.activate(DefaultKelmaSyncEndpoint, "maria@example.org"))
    }
}

private class InMemoryRegistry : LocalAccountRegistryStorage {
    private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String) {
        this.value = value
    }
}
