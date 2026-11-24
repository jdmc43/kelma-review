package tech.kelma.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SignInScreenUiTest {
    @Test
    fun desktopDeckListExposesAccountControls() = runComposeUiTest {
        val opened = AtomicBoolean(false)
        setContent {
            KelmaTheme {
                DesktopDeckListScreen(
                    decks = emptyList(),
                    signedIn = true,
                    syncing = false,
                    syncMessage = null,
                    syncMessageIsError = false,
                    studiedToday = 0,
                    syncedCardCount = 0,
                    localCardCount = 0,
                    syncedMediaBytes = 0,
                    canUndo = false,
                    onUndo = {},
                    onAdd = {},
                    onCreateDeck = { null },
                    deckManagement = DeckManagementActions(
                        onAddCards = {},
                        onBrowseCards = {},
                        onOptions = {},
                        onExport = {},
                        onRename = { _, _ -> null },
                        onDelete = { null },
                    ),
                    onOpenDeck = {},
                    onSignIn = {},
                    onSync = {},
                    onAccount = { opened.set(true) },
                )
            }
        }

        onNodeWithText("Account…").assertIsDisplayed().performClick()
        assertTrue(opened.get())
    }

    @Test
    fun optionalAccountActionsStayInAppOrReturnToLocalUse() = runComposeUiTest {
        val opened = AtomicReference<String?>(null)
        val registered = AtomicReference<Pair<String, String>?>(null)
        val reset = AtomicReference<String?>(null)
        val continued = AtomicBoolean(false)
        setContent {
            KelmaTheme {
                SignInScreen(
                    signingIn = false,
                    error = null,
                    onSignIn = { _, _, _ -> },
                    onRegister = { email, password -> registered.set(email to password) },
                    onRequestPasswordReset = reset::set,
                    onBack = { continued.set(true) },
                    onOpenUri = opened::set,
                )
            }
        }

        onNodeWithText("In Review, a Kelma account is optional and only needed for KelmaSync.")
            .assertIsDisplayed()
        onNodeWithTag("create-kelma-account").performClick()
        onNodeWithText("Create your Kelma account").assertIsDisplayed()
        onNodeWithTag("registration-email").performTextInput("NEW@Example.com")
        onNodeWithTag("registration-password").performTextInput("secret1")
        onNodeWithTag("registration-confirm-password").performTextInput("secret1")
        onNodeWithTag("registration-legal-consent").performClick()
        onNodeWithTag("registration-submit").performClick()
        assertEquals("new@example.com" to "secret1", registered.get())
        assertEquals(null, opened.get())

        onNodeWithText("Back to sign in").performClick()
        onNodeWithTag("forgot-password").performClick()
        onNodeWithText("Reset your password").assertIsDisplayed()
        onNodeWithTag("password-reset-email").performTextInput("person@example.com")
        onNodeWithTag("password-reset-submit").performClick()
        assertEquals("person@example.com", reset.get())
        assertEquals(null, opened.get())

        onNodeWithText("Back to sign in").performClick()
        onNodeWithTag("continue-without-account").performClick()
        assertTrue(continued.get())
    }

    @Test
    fun savedAccountOpensWithoutRequestingItsPassword() = runComposeUiTest {
        val submitted = AtomicReference<Pair<String, String>?>(null)
        val selected = AtomicReference<LocalAccountChoice?>(null)
        setContent {
            KelmaTheme {
                SignInScreen(
                    signingIn = false,
                    error = null,
                    accounts = listOf(
                        LocalAccountChoice("alice@example.com", DefaultKelmaSyncEndpoint),
                        LocalAccountChoice("bob@example.com", DefaultKelmaSyncEndpoint),
                    ),
                    onSignIn = { username, password, _ -> submitted.set(username to password) },
                    onSelectAccount = selected::set,
                    onBack = {},
                )
            }
        }

        onNodeWithTag("saved-account-alice@example.com").assertIsDisplayed().performClick()
        onNodeWithTag("saved-account-bob@example.com").assertIsDisplayed()
        onNodeWithTag("sign-in-username").assertTextContains("alice@example.com")

        assertEquals(LocalAccountChoice("alice@example.com", DefaultKelmaSyncEndpoint), selected.get())
        assertEquals(null, submitted.get())
    }

    @Test
    fun aSelfHostedServerIsChosenAtSignIn() = runComposeUiTest {
        val submitted = AtomicReference<Triple<String, String, String>?>(null)
        setContent {
            KelmaTheme {
                SignInScreen(
                    signingIn = false,
                    error = null,
                    onSignIn = { username, password, endpoint -> submitted.set(Triple(username, password, endpoint)) },
                    onBack = {},
                )
            }
        }

        onNodeWithTag("sign-in-server-summary").assertIsDisplayed()
        onNodeWithText("Kelma · sync2.kelma.tech").assertIsDisplayed()
        onNodeWithTag("create-kelma-account").assertIsDisplayed()
        onNodeWithTag("sign-in-change-server").performClick()

        onNodeWithTag("sign-in-server").performTextInput("http://sync.example.org")
        onNodeWithTag("sign-in-server-unencrypted").assertIsDisplayed()
        onNodeWithTag("sign-in-server").performTextClearance()
        onNodeWithTag("sign-in-server").performTextInput("Sync.Home.example/")
        onNodeWithTag("create-kelma-account").assertDoesNotExist()
        onNodeWithTag("forgot-password").assertDoesNotExist()
        onNodeWithTag("sign-in-username").performTextInput("maria@home.example")
        onNodeWithTag("sign-in-password").performTextInput("correct horse")
        onNodeWithTag("sign-in-submit").performClick()
        assertEquals(Triple("maria@home.example", "correct horse", "https://sync.home.example"), submitted.get())

        onNodeWithTag("sign-in-kelma-server").performClick()
        onNodeWithTag("create-kelma-account").assertIsDisplayed()
        onNodeWithTag("sign-in-submit").performClick()
        assertEquals(DefaultKelmaSyncEndpoint, submitted.get()?.third)
    }

    @Test
    fun anInvalidServerAddressBlocksSignIn() = runComposeUiTest {
        val submitted = AtomicBoolean(false)
        setContent {
            KelmaTheme {
                SignInScreen(
                    signingIn = false,
                    error = null,
                    onSignIn = { _, _, _ -> submitted.set(true) },
                    onBack = {},
                )
            }
        }

        onNodeWithTag("sign-in-change-server").performClick()
        onNodeWithTag("sign-in-server").performTextInput("ftp://files.example.org")
        onNodeWithTag("sign-in-username").performTextInput("maria")
        onNodeWithTag("sign-in-password").performTextInput("secret")
        onNodeWithTag("sign-in-submit").assertIsNotEnabled().performClick()
        assertTrue(!submitted.get())
    }

    @Test
    fun savedAccountsShowTheirServer() = runComposeUiTest {
        setContent {
            KelmaTheme {
                SignInScreen(
                    signingIn = false,
                    error = null,
                    accounts = listOf(
                        LocalAccountChoice("maria@example.org", DefaultKelmaSyncEndpoint),
                        LocalAccountChoice("maria@home.example", "https://sync.home.example"),
                    ),
                    onSignIn = { _, _, _ -> },
                    onBack = {},
                )
            }
        }

        onNodeWithTag("saved-account-maria@example.org").assertTextContains("Kelma · sync2.kelma.tech")
        onNodeWithTag("saved-account-maria@home.example").assertTextContains("https://sync.home.example")
    }

    @Test
    fun aSelfHostedAccountCannotBeDeletedThroughKelma() = runComposeUiTest {
        setContent {
            KelmaTheme {
                AccountLegalControlsDialog(
                    signedIn = true,
                    username = "maria@home.example",
                    working = false,
                    server = "https://sync.home.example",
                    onChooseAccount = {},
                    onSwitchAccount = {},
                    onSignOut = {},
                    onRemoveFromDevice = {},
                    onDeleteKelmaAccount = {},
                    onOpenUri = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("Signed in as maria@home.example on sync.home.example").assertIsDisplayed()
        onNodeWithTag("account-self-hosted").assertExists()
        onNodeWithTag("account-delete-kelma").assertDoesNotExist()
    }
}
