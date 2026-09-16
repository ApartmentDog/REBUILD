package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.ConfessionEntry
import com.evyr.rebuild.security.ConfessionPasswordManager
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BoothState { LOADING, SETUP, LOCKED, UNLOCKED }

/**
 * Reached via the APPLE cheat code. Gated by its own password, separate from
 * the app's PIN/biometric lock (ConfessionPasswordManager, not PinManager) --
 * unlocking the app does not unlock the booth. First-ever visit prompts to
 * set that password; every visit after that requires it.
 *
 * Entries are write-once ("sealed") -- there's deliberately no edit path,
 * only write, read back, or delete. Unlock state is per-screen-visit, held
 * in Compose state rather than persisted, so leaving and reopening the booth
 * (or backgrounding the app) asks for the password again.
 */
@Composable
fun ConfessionBoothScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val passwordManager = remember { ConfessionPasswordManager(context) }
    val scope = rememberCoroutineScope()

    var boothState by remember { mutableStateOf(BoothState.LOADING) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        boothState = if (passwordManager.hasPasswordSet()) BoothState.LOCKED else BoothState.SETUP
    }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text(
                "CONFESSION BOOTH",
                fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 13.sp, letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
        }
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)

        when (boothState) {
            BoothState.LOADING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NervMagenta)
            }

            BoothState.SETUP -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "SET A PASSWORD FOR THIS BOOTH",
                    fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 13.sp, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Separate from your app lock. Anything written here stays sealed behind it.",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 10.sp
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it; error = null },
                    label = { Text("Password", fontFamily = TerminalFontFamily) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = rebuildTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmInput,
                    onValueChange = { confirmInput = it; error = null },
                    label = { Text("Confirm", fontFamily = TerminalFontFamily) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = rebuildTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        when {
                            passwordInput.length < 4 -> error = "At least 4 characters"
                            passwordInput != confirmInput -> error = "Passwords don't match"
                            else -> scope.launch {
                                passwordManager.setPassword(passwordInput)
                                passwordInput = ""
                                confirmInput = ""
                                boothState = BoothState.UNLOCKED
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
                ) {
                    Text("SEAL IT", fontFamily = TerminalFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            BoothState.LOCKED -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "LOCKED",
                    fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 16.sp, letterSpacing = 3.sp
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it; error = null },
                    label = { Text("Password", fontFamily = TerminalFontFamily) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = rebuildTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            if (passwordManager.verifyPassword(passwordInput)) {
                                passwordInput = ""
                                boothState = BoothState.UNLOCKED
                            } else {
                                error = "Wrong password"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
                ) {
                    Text("UNLOCK", fontFamily = TerminalFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { showResetConfirm = true }) {
                    Text("Forgot it? Reset (keeps entries)", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 10.sp)
                }
            }

            BoothState.UNLOCKED -> ConfessionBoothContent(
                viewModel = viewModel,
                draft = draft,
                onDraftChange = { draft = it },
                onSeal = {
                    if (draft.isNotBlank()) {
                        viewModel.addConfessionEntry(draft.trim())
                        draft = ""
                    }
                }
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = EvaPanelBg,
            title = { Text("RESET PASSWORD?", fontFamily = TerminalFontFamily, color = NervRed) },
            text = {
                Text(
                    "This clears the current password so you can set a new one. Existing sealed entries are not touched or deleted.",
                    fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        passwordManager.clearPassword()
                        showResetConfirm = false
                        error = null
                        boothState = BoothState.SETUP
                    }
                }) { Text("RESET", fontFamily = TerminalFontFamily, color = NervRed) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("CANCEL", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }
}

@Composable
private fun ConfessionBoothContent(
    viewModel: MainViewModel,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSeal: () -> Unit
) {
    val entries by viewModel.confessionEntries.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Write it down. No one reads this but you.", fontFamily = TerminalFontFamily) },
                colors = rebuildTextFieldColors(),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSeal,
                enabled = draft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("SEAL", fontFamily = TerminalFontFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }

        HazardStripe(height = 3.dp, colorA = NervMagenta, colorB = EvaOutline)

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "NOTHING SEALED YET",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(entries, key = { it.id }) { entry ->
                    ConfessionEntryRow(
                        entry = entry,
                        expanded = expandedId == entry.id,
                        dateLabel = dateFormat.format(Date(entry.writtenAtEpochMs)),
                        onToggle = { expandedId = if (expandedId == entry.id) null else entry.id },
                        onDelete = { viewModel.deleteConfessionEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfessionEntryRow(
    entry: ConfessionEntry,
    expanded: Boolean,
    dateLabel: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(EvaPanelBg, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onToggle)) {
                Text(dateLabel, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp)
                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Text(entry.text, fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                } else {
                    Text(
                        entry.text.take(40) + if (entry.text.length > 40) "…" else "",
                        fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp, maxLines = 1
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = NervRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}
