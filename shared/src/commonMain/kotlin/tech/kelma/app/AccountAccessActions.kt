package tech.kelma.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class AccountAccessCallbacks(
    val signIn: (username: String, password: String, endpoint: String) -> Unit,
    val registerAccount: (String, String) -> Unit,
    val requestPasswordReset: (String) -> Unit,
    val clearFeedback: () -> Unit,
    val selectAccount: (LocalAccountChoice) -> Unit,
    val leaveAccount: () -> Unit,
)

@Suppress("LongParameterList")
internal fun accountAccessCallbacks(
    state: AppState,
    accountRegistry: LocalAccountRegistry,
    login: suspend (endpoint: String, username: String, password: String) -> LoginResponse,
    accountService: KelmaAccountService,
    scope: CoroutineScope,
    setPendingSignIn: (PendingAccountSignIn?) -> Unit,
    setOpeningSavedAccount: (Boolean) -> Unit,
    setDatabaseName: (String) -> Unit,
): AccountAccessCallbacks {
    val signIn: (String, String, String) -> Unit = signIn@{ username, password, requestedEndpoint ->
        if (state.working.value || !state.restored.value) return@signIn
        val endpoint = try {
            SyncServerAddress.normalize(requestedEndpoint)
        } catch (invalid: IllegalArgumentException) {
            state.error.value = invalid.message
            return@signIn
        }
        state.working.value = true
        state.error.value = null
        state.accountAccessMessage.value = null
        state.syncMessage.value = null
        scope.launch {
            try {
                // Each account belongs to the server it signed in to; the same
                // username on another server is a separate local account.
                val auth = login(endpoint, username, password)
                val targetDatabase = accountRegistry.activate(endpoint, username)
                state.clearDisplayedAccount()
                state.token.value = auth.token
                setPendingSignIn(PendingAccountSignIn(username, auth, endpoint))
                state.showSignIn.value = false
                state.destination.value = CollectionDestination.Sync
                state.restored.value = false
                setDatabaseName(targetDatabase)
            } catch (exception: Exception) {
                state.error.value = exception.message
                    ?: "Could not connect to ${SyncServerAddress.label(endpoint)}"
                state.working.value = false
            }
        }
    }

    fun launchRequest(request: suspend () -> String) {
        if (state.working.value || !state.restored.value) return
        state.working.value = true
        state.error.value = null
        state.accountAccessMessage.value = null
        scope.launch {
            try {
                state.accountAccessMessage.value = request()
            } catch (exception: Exception) {
                state.error.value = exception.message ?: "The account request could not be completed"
            } finally {
                state.working.value = false
            }
        }
    }

    val registerAccount: (String, String) -> Unit = { email, password ->
        launchRequest { accountService.register(email, password) }
    }
    val requestPasswordReset: (String) -> Unit = { email ->
        launchRequest { accountService.requestPasswordReset(email) }
    }
    val clearFeedback = {
        state.error.value = null
        state.accountAccessMessage.value = null
    }
    val selectAccount: (LocalAccountChoice) -> Unit = selectAccount@{ account ->
        if (state.working.value || !state.restored.value) return@selectAccount
        val targetDatabase = accountRegistry.databaseName(account.endpoint, account.username)
        if (targetDatabase == null) {
            state.error.value = "This saved account is no longer available on this device."
            return@selectAccount
        }
        state.working.value = true
        state.error.value = null
        state.accountAccessMessage.value = null
        state.syncMessage.value = null
        accountRegistry.activate(account.endpoint, account.username)
        setPendingSignIn(null)
        setOpeningSavedAccount(true)
        state.clearDisplayedAccount()
        state.showSignIn.value = true
        state.restored.value = false
        setDatabaseName(targetDatabase)
    }
    val leaveAccount: () -> Unit = leaveAccount@{
        if (state.working.value || !state.restored.value) return@leaveAccount
        state.working.value = true
        accountRegistry.deactivate()
        setPendingSignIn(null)
        setOpeningSavedAccount(false)
        state.clearDisplayedAccount()
        state.error.value = null
        state.accountAccessMessage.value = null
        state.syncMessage.value = null
        state.showSignIn.value = true
        state.restored.value = false
        setDatabaseName(GuestCollectionDatabaseName)
    }
    return AccountAccessCallbacks(
        signIn,
        registerAccount,
        requestPasswordReset,
        clearFeedback,
        selectAccount,
        leaveAccount,
    )
}
