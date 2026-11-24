package tech.kelma.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountServerSignInTest {
    @Test
    fun signInGoesToTheChosenServerAndKeepsItWithTheAccount() {
        val state = AppState().apply { restored.value = true }
        val registry = LocalAccountRegistry(MemoryRegistryStorage())
        val logins = mutableListOf<String>()
        var pending: PendingAccountSignIn? = null
        var database: String? = null
        val callbacks = accountAccessCallbacks(
            state = state,
            accountRegistry = registry,
            login = { endpoint, _, _ ->
                logins += endpoint
                LoginResponse("token-$endpoint", "client")
            },
            accountService = NoAccountService,
            scope = CoroutineScope(Dispatchers.Unconfined),
            setPendingSignIn = { pending = it },
            setOpeningSavedAccount = {},
            setDatabaseName = { database = it },
        )

        callbacks.signIn("maria@home.example", "secret", "sync.home.example/")

        assertEquals(listOf("https://sync.home.example"), logins)
        assertEquals("https://sync.home.example", pending?.endpoint)
        assertEquals("https://sync.home.example", registry.activeAccount()?.endpoint)
        assertEquals(registry.activeDatabaseName(), database)

        state.working.value = false
        state.restored.value = true
        callbacks.signIn("maria@home.example", "secret", DefaultKelmaSyncEndpoint)
        assertEquals(DefaultKelmaSyncEndpoint, pending?.endpoint)
        assertEquals(2, registry.accounts().size)
    }

    @Test
    fun anInvalidServerNeverReachesTheNetwork() {
        val state = AppState().apply { restored.value = true }
        val logins = mutableListOf<String>()
        val callbacks = accountAccessCallbacks(
            state = state,
            accountRegistry = LocalAccountRegistry(MemoryRegistryStorage()),
            login = { endpoint, _, _ ->
                logins += endpoint
                LoginResponse("token", "client")
            },
            accountService = NoAccountService,
            scope = CoroutineScope(Dispatchers.Unconfined),
            setPendingSignIn = {},
            setOpeningSavedAccount = {},
            setDatabaseName = {},
        )

        callbacks.signIn("maria", "secret", "ftp://files.example.org")

        assertEquals(emptyList(), logins)
        assertEquals(false, state.working.value)
        assertNull(state.token.value)
        assertEquals(true, state.error.value.orEmpty().isNotBlank())
    }
}

private class MemoryRegistryStorage : LocalAccountRegistryStorage {
    private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String) {
        this.value = value
    }
}

private object NoAccountService : KelmaAccountService {
    override suspend fun register(email: String, password: String): String = error("not used")
    override suspend fun requestPasswordReset(email: String): String = error("not used")
    override suspend fun deleteAccount(email: String, password: String) = error("not used")
    override fun close() = Unit
}
