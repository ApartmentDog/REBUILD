package com.evyr.rebuild

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import com.evyr.rebuild.nfc.NfcHelper
import com.evyr.rebuild.security.PinManager
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.screens.*
import com.evyr.rebuild.ui.theme.RebuildTheme

private enum class Screen { LOCK, PERMISSIONS, DAILY_QUOTE, DASHBOARD, NOTEBOOK, SETTINGS, NFC_TAGS, LOCATIONS, GUIDED_SESSIONS, HIDDEN_MEDIA, TAROT_PULL, TAROT_LOG, BOOK_LIBRARY, BOOK_READER, CONFESSION_BOOTH, HOARD }
private enum class NfcStatus { OK, DISABLED, NO_HARDWARE }

// FragmentActivity rather than ComponentActivity: BiometricPrompt requires a
// FragmentActivity host. FragmentActivity extends ComponentActivity, so setContent
// and the rest of the Compose setup are unaffected.
class MainActivity : FragmentActivity() {

    private lateinit var nfcHelper: NfcHelper
    private lateinit var viewModel: MainViewModel
    private var pendingScanUid by mutableStateOf<String?>(null)
    private var pendingScanIntent: Intent? = null
    private var nfcStatus by mutableStateOf(NfcStatus.OK)

    // Armed registration: label is chosen BEFORE the tap, so the write happens
    // the instant onNewIntent fires while the tag is still physically present —
    // no gap between "user finishes typing" and "write attempted" that could let
    // the tag go out of range (that gap was the old design's actual bug: Android
    // throws "Permission Denial: Tag ... is out of date" once the tag leaves the
    // field, and typing a label takes longer than the tag stays in range).
    private var armedLabel by mutableStateOf<String?>(null)
    var lastWriteResult by mutableStateOf<NfcHelper.WriteResult?>(null)
        private set

    fun armPendingRegistration(label: String) {
        armedLabel = label
        lastWriteResult = null
    }

    fun disarmPendingRegistration() {
        armedLabel = null
    }

    private fun computeNfcStatus(): NfcStatus = when {
        !nfcHelper.isNfcAvailable() -> NfcStatus.NO_HARDWARE
        !nfcHelper.isNfcEnabled() -> NfcStatus.DISABLED
        else -> NfcStatus.OK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = RebuildDatabase.getInstance(applicationContext)
        val repo = AppRepository(db)
        val pinManager = PinManager(applicationContext)
        val quoteTracker = com.evyr.rebuild.data.DailyQuoteTracker(applicationContext)
        com.evyr.rebuild.data.WeeklyBackupWorker.schedule(applicationContext)

        // Re-register geofences if they were dropped (location switched off,
        // a failed registration) or it's been over a day. Reboots and updates
        // are handled by BootReceiver; this catches everything else.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.evyr.rebuild.location.GeofenceSync.syncIfNeeded(applicationContext, "on app open")
        }
        // Through ViewModelProvider so it survives rotation/config changes and
        // its coroutines are cancelled when the activity really finishes. Built
        // directly, every recreation leaked a VM with live collectors.
        viewModel = androidx.lifecycle.ViewModelProvider(
            this, com.evyr.rebuild.ui.MainViewModelFactory(repo)
        )[MainViewModel::class.java]
        val vault = com.evyr.rebuild.data.HardModeVault(applicationContext)

        // Clears reminders orphaned by a deleted directive and re-arms the rest.
        // Orphans otherwise fire daily with no directive to name them. Running
        // it here as well as on boot means they're gone the next time the app
        // opens rather than whenever the device happens to restart.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.evyr.rebuild.notification.ReminderScheduler.rescheduleAll(applicationContext, repo)
            com.evyr.rebuild.notification.DigestScheduler.scheduleAll(applicationContext, repo.getNotificationPrefs())
        }

        nfcHelper = NfcHelper()
        nfcHelper.init(this)
        nfcStatus = computeNfcStatus()

        setContent {
            RebuildTheme {
            com.evyr.rebuild.ui.components.ConsoleFrame {
                var screen by remember { mutableStateOf(Screen.LOCK) }
                val scope = rememberCoroutineScope()
                var showCheatCodeDialog by remember { mutableStateOf(false) }
                var cheatCodeError by remember { mutableStateOf<String?>(null) }
                var selectedBook by remember { mutableStateOf<com.evyr.rebuild.data.BookItem?>(null) }

                // A tag scanned while NOT on the NFC_TAGS screen is treated as a normal
                // confirm-scan (handleTagScan no-ops silently if the tag isn't registered
                // yet — see AppRepository.handleTagScan). Jump to the registration screen
                // automatically only if the scan doesn't match a known tag.
                // Keyed on screen too, so a tap that lands on the lock screen is
                // picked up once you're past it.
                LaunchedEffect(pendingScanUid, screen) {
                    val uid = pendingScanUid ?: return@LaunchedEffect
                    if (screen != Screen.NFC_TAGS) {
                        // Straight from the database: viewModel.tags.value starts as
                        // an empty list and stays that way until something collects
                        // it, so a tap that cold-started the app always looked like
                        // an unknown tag.
                        val tag = repo.findTag(uid)
                        if (tag != null) {
                            if (tag.opensChecklist) {
                                startActivity(
                                    android.content.Intent(
                                        this@MainActivity,
                                        com.evyr.rebuild.nfc.DoorChecklistActivity::class.java
                                    )
                                )
                            } else {
                                com.evyr.rebuild.nfc.DoorState.recordTap(this@MainActivity, uid)
                                viewModel.handleTagScan(uid, locationLabel = null)
                            }
                            pendingScanUid = null
                        } else if (screen != Screen.LOCK && screen != Screen.PERMISSIONS) {
                            // Unknown tag opens registration -- but never from the
                            // lock screen. This used to jump straight past the PIN.
                            screen = Screen.NFC_TAGS
                            // pendingScanUid stays set — NfcRegistrationScreen consumes it
                        }
                    }
                }

                when (screen) {
                    Screen.LOCK -> PinLockScreen(
                        pinManager = pinManager,
                        onUnlocked = { screen = Screen.PERMISSIONS }
                    )
                    Screen.PERMISSIONS -> PermissionFlowScreen(
                        onComplete = {
                            // Show the quote only on the first unlock of the day, and
                            // only if there's actually something to show -- an empty
                            // Mindset list would otherwise mean a blank card.
                            //
                            // mindsetItems.value was read here before, which is a
                            // StateFlow seeded at emptyList() that only becomes
                            // accurate once something has been collecting it for a
                            // moment -- read synchronously this early, right at
                            // cold start, it was reliably still empty even with
                            // real Mindset entries in the database, so the quote
                            // never fired. mindsetItemsNow() awaits the actual
                            // value instead.
                            scope.launch {
                                val mindsetNow = viewModel.mindsetItemsNow()
                                screen = if (mindsetNow.isNotEmpty() && quoteTracker.shouldShowToday()) {
                                    Screen.DAILY_QUOTE
                                } else {
                                    Screen.DASHBOARD
                                }
                            }
                        }
                    )
                    Screen.DAILY_QUOTE -> DailyQuoteScreen(
                        mindset = viewModel.mindsetItems.collectAsState().value,
                        quoteTracker = quoteTracker,
                        onContinue = {
                            quoteTracker.markShownToday()
                            screen = Screen.DASHBOARD
                        }
                    )
                    Screen.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        nfcDisabled = nfcStatus != NfcStatus.OK,
                        nfcUnavailableReason = if (nfcStatus == NfcStatus.NO_HARDWARE) "No NFC hardware on this device" else "NFC is turned off — enable it in system settings",
                        onOpenSecretPrompt = { showCheatCodeDialog = true },
                        onOpenNotebook = { screen = Screen.NOTEBOOK },
                        onOpenSettings = { screen = Screen.SETTINGS }
                    )
                    Screen.NOTEBOOK -> NotebookScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD },
                        onOpenNfcTags = { screen = Screen.NFC_TAGS },
                        onOpenLocations = { screen = Screen.LOCATIONS },
                        onOpenGuidedSessions = { screen = Screen.GUIDED_SESSIONS },
                        onResetDailyQuote = {
                            quoteTracker.reset()
                            screen = Screen.DAILY_QUOTE
                        }
                    )
                    Screen.NFC_TAGS -> NfcRegistrationScreen(
                        viewModel = viewModel,
                        pendingScanUid = pendingScanUid,
                        onClearPendingScan = { pendingScanUid = null },
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.LOCATIONS -> GeofencePlacesScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.GUIDED_SESSIONS -> GuidedSessionScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.HIDDEN_MEDIA -> HiddenMediaPlayerScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD }
                    )
                    Screen.TAROT_PULL -> TarotPullScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD },
                        onOpenLog = { screen = Screen.TAROT_LOG }
                    )
                    Screen.TAROT_LOG -> TarotLogScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD }
                    )
                    Screen.BOOK_LIBRARY -> BookLibraryScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD },
                        onOpenBook = { book ->
                            selectedBook = book
                            screen = Screen.BOOK_READER
                        }
                    )
                    Screen.BOOK_READER -> {
                        val book = selectedBook
                        if (book != null) {
                            BookReaderScreen(
                                viewModel = viewModel,
                                book = book,
                                onBack = { screen = Screen.BOOK_LIBRARY }
                            )
                        } else {
                            // Shouldn't happen (BOOK_READER is only reached via
                            // onOpenBook setting selectedBook first), but falls
                            // back to the library rather than crashing on a null.
                            screen = Screen.BOOK_LIBRARY
                        }
                    }
                    Screen.CONFESSION_BOOTH -> ConfessionBoothScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD }
                    )
                    Screen.HOARD -> HoardScreen(
                        viewModel = viewModel,
                        onBack = { screen = Screen.DASHBOARD }
                    )
                }

                if (showCheatCodeDialog) {
                    var codeInput by remember { mutableStateOf("") }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showCheatCodeDialog = false; cheatCodeError = null },
                        containerColor = com.evyr.rebuild.ui.theme.NervPanel,
                        title = {
                            androidx.compose.material3.Text(
                                "///",
                                fontFamily = com.evyr.rebuild.ui.theme.TerminalFontFamily,
                                color = com.evyr.rebuild.ui.theme.NervMagenta
                            )
                        },
                        text = {
                            Column {
                                androidx.compose.material3.OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = { codeInput = it },
                                    placeholder = {
                                        androidx.compose.material3.Text(
                                            "code",
                                            fontFamily = com.evyr.rebuild.ui.theme.TerminalFontFamily
                                        )
                                    },
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = com.evyr.rebuild.ui.theme.NervMagenta,
                                        unfocusedTextColor = com.evyr.rebuild.ui.theme.NervMagenta,
                                        focusedBorderColor = com.evyr.rebuild.ui.theme.NervMagenta,
                                        unfocusedBorderColor = com.evyr.rebuild.ui.theme.NervDim
                                    )
                                )
                                cheatCodeError?.let { err ->
                                    Spacer(Modifier.height(8.dp))
                                    androidx.compose.material3.Text(
                                        err,
                                        fontFamily = com.evyr.rebuild.ui.theme.TerminalFontFamily,
                                        color = com.evyr.rebuild.ui.theme.NervRed,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                // Fixed codes by request -- no Settings field, no stored
                                // hash. Case-insensitive so it's not fussy to type.
                                val entered = codeInput.trim()
                                if (entered.equals("FREEPLAY", ignoreCase = true)) {
                                    showCheatCodeDialog = false
                                    cheatCodeError = null
                                    screen = Screen.HIDDEN_MEDIA
                                } else if (entered.equals("SECFOLDER", ignoreCase = true)) {
                                    // Secure Folder's package is fixed across One UI versions.
                                    // getLaunchIntentForPackage returns null if it's not set up
                                    // on this device/profile -- surfaced as an error rather than
                                    // silently doing nothing.
                                    val launchIntent = packageManager.getLaunchIntentForPackage("com.samsung.knox.securefolder")
                                    if (launchIntent != null) {
                                        showCheatCodeDialog = false
                                        cheatCodeError = null
                                        startActivity(launchIntent)
                                    } else {
                                        cheatCodeError = "Secure Folder not found on this device"
                                    }
                                } else if (entered.equals("SWORD", ignoreCase = true)) {
                                    showCheatCodeDialog = false
                                    cheatCodeError = null
                                    screen = Screen.TAROT_PULL
                                } else if (entered.equals("MIRROR", ignoreCase = true)) {
                                    showCheatCodeDialog = false
                                    cheatCodeError = null
                                    screen = Screen.TAROT_LOG
                                } else if (entered.equals("ALEXANDRIA", ignoreCase = true)) {
                                    showCheatCodeDialog = false
                                    cheatCodeError = null
                                    screen = Screen.BOOK_LIBRARY
                                } else if (entered.equals("APPLE", ignoreCase = true)) {
                                    showCheatCodeDialog = false
                                    cheatCodeError = null
                                    screen = Screen.CONFESSION_BOOTH
                                } else if (entered.equals(com.evyr.rebuild.data.HardModeVault.CODE, ignoreCase = true)) {
                                    val cleared = viewModel.hardItems.value.count { it.hardClearedEpochMs != null }
                                    if (vault.isUnlocked(cleared)) {
                                        showCheatCodeDialog = false
                                        cheatCodeError = null
                                        screen = Screen.HOARD
                                    } else {
                                        cheatCodeError = "SEALED · ${cleared.coerceAtMost(com.evyr.rebuild.data.HardModeVault.REQUIRED)} / ${com.evyr.rebuild.data.HardModeVault.REQUIRED} 🔥"
                                    }
                                } else {
                                    cheatCodeError = "wrong"
                                }
                            }) {
                                androidx.compose.material3.Text(
                                    "ENTER",
                                    fontFamily = com.evyr.rebuild.ui.theme.TerminalFontFamily,
                                    color = com.evyr.rebuild.ui.theme.NervGreen
                                )
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showCheatCodeDialog = false; cheatCodeError = null }) {
                                androidx.compose.material3.Text(
                                    "CANCEL",
                                    fontFamily = com.evyr.rebuild.ui.theme.TerminalFontFamily,
                                    color = com.evyr.rebuild.ui.theme.NervDim
                                )
                            }
                        }
                    )
                }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcStatus = computeNfcStatus()
        nfcHelper.enableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        nfcHelper.disableForegroundDispatch(this)
        // Redraw the home-screen widget on the way out, so changes made in the app
        // are reflected without waiting for the 30-minute update window.
        com.evyr.rebuild.widget.RebuildWidgetProvider.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingScanIntent = intent

        val label = armedLabel
        if (label != null) {
            // Tag is present right now — write immediately, no dialog in between.
            val result = nfcHelper.writeLabelToTag(intent, label)
            lastWriteResult = result
            nfcHelper.extractTagUid(intent)?.let { uid -> pendingScanUid = uid }
            // Only disarm on success — a failed write (tag pulled away mid-write,
            // wrong tag type, etc.) should stay armed so the very next tap retries
            // with the same label instead of falling back to plain UID capture.
            if (result is NfcHelper.WriteResult.Success) {
                armedLabel = null
            }
        } else {
            nfcHelper.extractTagUid(intent)?.let { uid -> pendingScanUid = uid }
        }
    }

    fun readLabelFromPendingTag(): String? {
        val intent = pendingScanIntent ?: return null
        return nfcHelper.readLabelFromTag(intent)
    }
}
