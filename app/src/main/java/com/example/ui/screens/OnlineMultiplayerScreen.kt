package com.example.ui.screens

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.remote.GoogleAuthManager
import com.example.data.remote.GoogleSignInResult
import com.example.data.remote.MultiplayerRepository
import com.example.data.remote.authStateFlow
import com.example.game.audio.SoundEffectsManager
import com.example.ui.viewmodel.MultiplayerMatchStage
import com.example.ui.viewmodel.MultiplayerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun OnlineMultiplayerRoot(
    isSoundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onBackToSolo: () -> Unit
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        // Navigation-level auth gating: no Firestore ViewModel or listeners created until signed in
        MultiplayerAuthGateScreen(
            onBackToSolo = onBackToSolo
        )
    } else {
        val context = LocalContext.current
        val soundManager = remember(context) {
            SoundEffectsManager(context.applicationContext).apply {
                isMuted = !isSoundEnabled
            }
        }
        LaunchedEffect(isSoundEnabled) {
            soundManager.isMuted = !isSoundEnabled
        }

        val viewModel: MultiplayerViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) as Application
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    val repo = MultiplayerRepository(db)
                    val name = user.displayName?.takeIf { it.isNotBlank() } ?: "CyberPilot"
                    MultiplayerViewModel(
                        repository = repo,
                        currentUserId = user.uid,
                        playerDisplayName = name,
                        soundManager = soundManager
                    )
                }
            }
        )

        OnlineMultiplayerFlow(
            viewModel = viewModel,
            isSoundEnabled = isSoundEnabled,
            onToggleSound = onToggleSound,
            onSignOut = { GoogleAuthManager.signOut() },
            onBackToSolo = onBackToSolo
        )
    }
}

@Composable
private fun MultiplayerAuthGateScreen(
    onBackToSolo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        GoogleAuthManager.attemptSilentSignIn(context)
        isLoading = false
    }

    BackHandler {
        onBackToSolo()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
            border = BorderStroke(1.2.dp, Color(0xFF1E3A5F)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("pvp_auth_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToSolo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("auth_back_to_solo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Solo",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "WORLDWIDE ONLINE PvP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 1.2.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x4400E5FF), Color(0x1100E5FF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "Sign In to Battle Anywhere",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Connect with any player over the internet via Quick Match or private Room Code. Guns survive 1–2 hits — only 3 simultaneous hits eliminate!",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                if (errorText != null) {
                    Surface(
                        color = Color(0x33EF4444),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorText!!,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isLoading) return@Button
                        errorText = null
                        isLoading = true
                        scope.launch {
                            when (val res = GoogleAuthManager.signInInteractive(context)) {
                                is GoogleSignInResult.Success -> {
                                    isLoading = false
                                }
                                is GoogleSignInResult.Cancelled -> {
                                    isLoading = false
                                    errorText = "Google Sign-In was dismissed. Tap again to choose an account."
                                }
                                is GoogleSignInResult.Error -> {
                                    isLoading = false
                                    errorText = res.message
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A2FF)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("SIGNING IN...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CONTINUE WITH GOOGLE",
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }

                TextButton(
                    onClick = onBackToSolo,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Back to Offline Solo Levels", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OnlineMultiplayerFlow(
    viewModel: MultiplayerViewModel,
    isSoundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onSignOut: () -> Unit,
    onBackToSolo: () -> Unit
) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val onlineProfile by viewModel.onlineProfile.collectAsStateWithLifecycle()
    val selectedGunType by viewModel.selectedGunType.collectAsStateWithLifecycle()
    val gameTick by viewModel.gameTick.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var roomCodeInput by remember { mutableStateOf("") }

    BackHandler {
        when (stage) {
            is MultiplayerMatchStage.Hub -> onBackToSolo()
            else -> viewModel.leaveOrCancelMatch()
        }
    }

    when (val s = stage) {
        is MultiplayerMatchStage.Hub,
        is MultiplayerMatchStage.Connecting -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0F19))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToSolo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(Color(0xFF151C2C))
                            .testTag("pvp_hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Solo",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "INTERNET PvP ARENA",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${viewModel.playerDisplayName} • ${onlineProfile?.onlineWins ?: 0} Wins / ${onlineProfile?.onlineMatches ?: 0} Matches",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(Color(0xFF151C2C))
                            .testTag("pvp_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out",
                            tint = Color(0xFFF87171)
                        )
                    }
                }

                // 3-Simultaneous-Hit Rule Callout
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141E30)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "COMBAT RULE: 3 SIMULTANEOUS HITS TO KILL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFBBF24),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "A single shot only stuns and knocks back the rival gun. Land 3 hits simultaneously (or within a 1.8s burst window, such as a 3-pellet Scatter blast or rapid ricochet combo) before their shield resets!",
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        color = Color(0x33EF4444),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Worldwide Quick Match
                Button(
                    onClick = { viewModel.startQuickMatch() },
                    enabled = s !is MultiplayerMatchStage.Connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A2FF)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("quick_match_button")
                ) {
                    if (s is MultiplayerMatchStage.Connecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("CONNECTING TO CLOUD ARENA...", fontWeight = FontWeight.Black, color = Color.White)
                    } else {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("QUICK MATCH (ANYWHERE)", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
                    }
                }

                // Private Room Code Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "PLAY WITH A FRIEND ANYWHERE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.createPrivateRoom() },
                            enabled = s !is MultiplayerMatchStage.Connecting,
                            border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_private_room_button")
                        ) {
                            Text(
                                text = "CREATE PRIVATE ROOM CODE",
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = roomCodeInput,
                                onValueChange = { roomCodeInput = it.uppercase().take(12) },
                                label = { Text("Enter Friend's Room Code") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pvp_join_code_input")
                            )

                            Button(
                                onClick = { viewModel.joinRoomByCode(roomCodeInput) },
                                enabled = roomCodeInput.trim().length >= 4 && s !is MultiplayerMatchStage.Connecting,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("pvp_join_code_button")
                            ) {
                                Text("JOIN", fontWeight = FontWeight.Black, color = Color(0xFF022C22))
                            }
                        }
                    }
                }
            }
        }

        is MultiplayerMatchStage.WaitingForOpponent -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0F19))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .testTag("waiting_room_card")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Text(
                            text = if (s.room.isPublic) "SEARCHING WORLDWIDE..." else "WAITING FOR FRIEND",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Share this Room Code with any player on another device anywhere on the internet:",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )

                        Surface(
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.5.dp, Color(0xFFFBBF24)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(s.room.roomCode))
                                    Toast.makeText(context, "Copied code: ${s.room.roomCode}", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("copy_pvp_room_code")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = s.room.roomCode,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFBBF24),
                                    letterSpacing = 4.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Room Code",
                                    tint = Color(0xFFFBBF24)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.leaveOrCancelMatch() },
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("cancel_waiting_room_button")
                        ) {
                            Text("CANCEL ROOM", fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        is MultiplayerMatchStage.ActiveMatch,
        is MultiplayerMatchStage.MatchFinished -> {
            val oppName = when (s) {
                is MultiplayerMatchStage.ActiveMatch -> s.opponentName
                is MultiplayerMatchStage.MatchFinished -> s.opponentName
                else -> "Rival"
            }
            Box(modifier = Modifier.fillMaxSize()) {
                ArenaGameScreen(
                    engine = viewModel.pvpEngine,
                    gameTick = gameTick,
                    selectedGunType = selectedGunType,
                    isPaused = false,
                    isLevelsMenuOpen = false,
                    isSoundEnabled = isSoundEnabled,
                    isMultiplayerMode = true,
                    opponentName = oppName,
                    onInitArenaBounds = { l, t, r, b -> viewModel.initializeArenaBounds(l, t, r, b) },
                    onTapScreen = { viewModel.tapToShoot() },
                    onSelectGun = { viewModel.selectGunType(it) },
                    onToggleSound = onToggleSound,
                    onRestartLevel = {},
                    onNextLevel = {},
                    onExitArena = { viewModel.leaveOrCancelMatch() }
                )

                if (s is MultiplayerMatchStage.MatchFinished) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xA8050914)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xF5111827)),
                            border = BorderStroke(
                                1.5.dp,
                                if (s.didWin) Color(0xFF10B981) else Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .padding(24.dp)
                                .widthIn(max = 340.dp)
                                .fillMaxWidth()
                                .testTag("pvp_result_dialog")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (s.didWin) "VICTORY!" else "ELIMINATED",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (s.didWin) Color(0xFF34D399) else Color(0xFFF87171)
                                )
                                Text(
                                    text = if (s.didWin) {
                                        "You shattered ${s.opponentName}'s gun with 3 simultaneous hits!"
                                    } else {
                                        "${s.opponentName} landed 3 simultaneous hits on your gun!"
                                    },
                                    fontSize = 14.sp,
                                    color = Color(0xFFE2E8F0),
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.leaveOrCancelMatch() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A2FF)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("pvp_back_to_hub_button")
                                ) {
                                    Text("BACK TO PvP LOBBY", fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
