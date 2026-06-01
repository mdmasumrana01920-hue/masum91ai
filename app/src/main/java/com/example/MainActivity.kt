package com.example // আপনার প্রজেক্টের প্যাকেজ নাম ঠিক রাখুন

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.db.AppDatabase
import com.example.data.model.CalendarEvent
import com.example.data.model.CommandLog
import com.example.data.model.Email
import com.example.data.repository.AssistantRepository
import com.example.ui.AssistantViewModel
import com.example.ui.AssistantViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.rememberColorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val isTtsMuted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Local Database and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = AssistantRepository(
            commandLogDao = database.commandLogDao(),
            emailDao = database.emailDao(),
            calendarEventDao = database.calendarEventDao(),
            context = this
        )

        // Setup ViewModel
        val factory = AssistantViewModelFactory(repository)
        val viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[AssistantViewModel::class.java]

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Observe TTS events from ViewModel
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ttsSpeakEvent.collectLatest { text ->
                    if (!isTtsMuted.value && isTtsInitialized) {
                        speakOut(text)
                    }
                }
            }
        }

        setContent {
            MyApplicationTheme {
                val isMuted by isTtsMuted
                MainScreen(
                    viewModel = viewModel,
                    isMuted = isMuted,
                    onToggleMute = { isTtsMuted.value = !isTtsMuted.value },
                    onReplaySpeak = { text -> speakOut(text) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.language = Locale.US
        } else {
            Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakOut(text: String) {
        if (!isTtsInitialized || tts == null) return

        // Context-aware language selection: speak Bengali if text contains Bengali letters
        if (hasBengaliCharacters(text)) {
            tts?.language = Locale("bn", "BD")
        } else {
            tts?.language = Locale.US
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AssistantSpeechId")
    }

    private fun hasBengaliCharacters(text: String): Boolean {
        for (char in text) {
            if (char in '\u0980'..'\u09FF') {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: AssistantViewModel,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onReplaySpeak: (String) -> Unit
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val unreadMailCount by viewModel.unreadEmailCount.collectAsState()
    val colors = rememberColorTheme()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBarSection(
                isMuted = isMuted,
                onToggleMute = onToggleMute,
                viewModel = viewModel
            )
        },
        bottomBar = {
            BottomNavigationSection(
                activeTab = activeTab,
                unreadCount = unreadMailCount,
                onTabSelected = { viewModel.setActiveTab(it) }
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                "ASSISTANT" -> AssistantTabScreen(viewModel, onReplaySpeak)
                "EMAILS" -> EmailsTabScreen(viewModel)
                "CALENDAR" -> CalendarTabScreen(viewModel = viewModel)
                "LOGS" -> LogsTabScreen(viewModel)
            }
        }
    }
}

@Composable
fun TopAppBarSection(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    viewModel: AssistantViewModel
) {
    val colors = rememberColorTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "FARIYA AI", // আপনার রিকোয়েস্ট অনুযায়ী নাম পরিবর্তন করা হয়েছে
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = colors.primary,
                letterSpacing = 2.sp
            )
            Text(
                text = "Background Voice Engine",
                fontSize = 11.sp,
                color = colors.slateGray,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.lightPurple.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = "Mute or Unmute Voice",
                    tint = if (isMuted) Color(0xFFE63946) else colors.primary
                )
            }
        }
    }
}

@Composable
fun BottomNavigationSection(
    activeTab: String,
    unreadCount: Int,
    onTabSelected: (String) -> Unit
) {
    val colors = rememberColorTheme()
    NavigationBar(
        containerColor = colors.surface,
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == "ASSISTANT",
            onClick = { onTabSelected("ASSISTANT") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "ASSISTANT") Icons.Filled.KeyboardVoice else Icons.Outlined.KeyboardVoice,
                    contentDescription = "Assistant Portal"
                )
            },
            label = { Text("Assistant", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = colors.primary,
                unselectedIconColor = colors.slateGray,
                selectedTextColor = colors.primary,
                unselectedTextColor = colors.slateGray,
                indicatorColor = colors.lightPurple
            ),
            modifier = Modifier.testTag("nav_assistant")
        )

        NavigationBarItem(
            selected = activeTab == "EMAILS",
            onClick = { onTabSelected("EMAILS") },
            icon = {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(containerColor = colors.primary) {
                                  Text(unreadCount.toString(), color = Color.White)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (activeTab == "EMAILS") Icons.Filled.Email else Icons.Outlined.Email,
                        contentDescription = "Gmail Simulator"
                    )
                }
            },
            label = { Text("Gmail Box", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = colors.primary,
                unselectedIconColor = colors.slateGray,
                selectedTextColor = colors.primary,
                unselectedTextColor = colors.slateGray,
                indicatorColor = colors.lightPurple
            ),
            modifier = Modifier.testTag("nav_emails")
        )

        NavigationBarItem(
            selected = activeTab == "CALENDAR",
            onClick = { onTabSelected("CALENDAR") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "CALENDAR") Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                    contentDescription = "Calendar Assistant"
                )
            },
            label = { Text("Calendar", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = colors.primary,
                unselectedIconColor = colors.slateGray,
                selectedTextColor = colors.primary,
                unselectedTextColor = colors.slateGray,
                indicatorColor = colors.lightPurple
            ),
            modifier = Modifier.testTag("nav_calendar")
        )

        NavigationBarItem(
            selected = activeTab == "LOGS",
            onClick = { onTabSelected("LOGS") },
            icon = {
                Icon(
                    imageVector = if (activeTab == "LOGS") Icons.Filled.Code else Icons.Outlined.Code,
                    contentDescription = "Action Logger"
                )
            },
            label = { Text("Log Console", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = colors.primary,
                unselectedIconColor = colors.slateGray,
                selectedTextColor = colors.primary,
                unselectedTextColor = colors.slateGray,
                indicatorColor = colors.lightPurple
            ),
            modifier = Modifier.testTag("nav_logs")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantTabScreen(
    viewModel: AssistantViewModel,
    onReplaySpeak: (String) -> Unit
) {
    val isListening by viewModel.isListening.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val commandInput by viewModel.commandInput.collectAsState()
    val logs by viewModel.allLogs.collectAsState()
    val colors = rememberColorTheme()

    val lastLog = logs.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(colors.primary, colors.secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "How can I ",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        lineHeight = 36.sp,
                        color = colors.textPrimary
                    )
                )
                Text(
                    text = "help",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        lineHeight = 36.sp,
                        color = colors.primary
                    )
                )
                Text(
                    text = " you today?",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                        lineHeight = 36.sp,
                        color = colors.textPrimary
                    )
                )
            }
        }

        // Animated Orb
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedMicWaveform(
                isListening = isListening,
                onClick = { viewModel.toggleListening() }
            )
        }

        // Processing Status
        Text(
            text = when {
                isListening -> "Listening... Tap Orb to Process"
                isLoading -> "Analyzing background command..."
                else -> "Tap the orb to speak, or type below"
            },
            fontSize = 13.sp,
            color = if (isListening) colors.primary else colors.slateGray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Custom Dynamic Response Card Terminal
        lastLog?.let { log ->
            ResponseTerminalCard(log = log, onReplaySpeak = onReplaySpeak, viewModel = viewModel)
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderGray.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        text = "SUGGESTED COMMANDS",
                        fontSize = 11.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• \"Check my emails\"\n• \"কোম ইমেইল চেক করো\"\n• \"Send a mail to Rana about launch specs\"",
                        fontSize = 13.sp,
                        color = colors.slateGray,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slide suggestion pill tags
        HorizontalSuggestionsList(
            suggestions = viewModel.suggestions,
            onSuggestionClicked = { viewModel.submitCommand(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Footer Listening & Typing input Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(colors.neutralGray)
                .border(1.dp, colors.borderGray.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = commandInput,
                onValueChange = { viewModel.setCommandInput(it) },
                placeholder = {
                    Text(
                        text = if (isListening) "Listening for your voice..." else "Listening or typed command...",
                        color = colors.slateGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                maxLines = 1,
                leadingIcon = {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.KeyboardVoice else Icons.Filled.Edit,
                        contentDescription = "Input action",
                        tint = colors.primary
                    )
                }
            )

            Row(
                modifier = Modifier.padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .clickable { viewModel.toggleListening() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(colors.primary, RoundedCornerShape(2.dp))
                    )
                }

                // Send action
                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            viewModel.submitCommand(commandInput)
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(colors.primary, CircleShape)
                        .testTag("send_command_button"),
                    enabled = commandInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Submit written command",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedMicWaveform(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val colors = rememberColorTheme()

    val animatedScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 1.5f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val animatedScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) 2.0f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp * animatedScale2)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.secondary.copy(alpha = if (isListening) 0.22f else 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(110.dp * animatedScale1)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = if (isListening) 0.35f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Canvas(
            modifier = Modifier
                .size(90.dp)
                .shadow(12.dp, CircleShape, ambientColor = colors.primary, spotColor = colors.secondary)
        ) {
            val centerBrush = Brush.linearGradient(
                colors = if (isListening) {
                    listOf(colors.primary, colors.secondary)
                } else {
                    listOf(colors.lightPurple, colors.surface)
                }
            )
            drawCircle(brush = centerBrush)

            if (isListening) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = size.minDimension / 2.2f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Icon(
            imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isListening) "Stop Listening" else "Start Listening",
            tint = if (isListening) Color.White else colors.primary,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
fun ResponseTerminalCard(
    log: CommandLog,
    onReplaySpeak: (String) -> Unit,
    viewModel: AssistantViewModel
) {
    val colors = rememberColorTheme()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.lightPurple)
                .border(1.dp, colors.primary.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CURRENT COMMAND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primary,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "\"${log.commandText}\"",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    lineHeight = 24.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surface)
                .border(1.dp, colors.borderGray, RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "ASSISTANT LOGIC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.slateGray,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = if (log.detectedAction != "NONE") "Invoking Gmail" else "General Chat Response",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.neutralGray)
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = if (log.detectedAction != "NONE") Icons.Filled.Email else Icons.Filled.AutoAwesome,
                            contentDescription = "Action Indicator icon",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = log.responseSpeech,
                    fontSize = 13.sp,
                    color = colors.slateGray,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setActiveTab("EMAILS") },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (log.detectedAction == "GMAIL_SEND") "Open Sent Hub" else "Open Gmail",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { onReplaySpeak(log.responseSpeech) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.lightPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Listen voice",
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Listen",
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalSuggestionsList(
    suggestions: List<String>,
    onSuggestionClicked: (String) -> Unit
) {
    val colors = rememberColorTheme()
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestions) { suggest ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.lightPurple.copy(alpha = 0.5f))
                        .border(1.dp, colors.borderGray.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { onSuggestionClicked(suggest) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = suggest,
                        fontSize = 11.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EmailsTabScreen(viewModel: AssistantViewModel) {
    val displayedEmails by viewModel.displayedEmails.collectAsState()
    val activeFolder by viewModel.emailFolder.collectAsState()
    val selectedEmail by viewModel.selectedEmail.collectAsState()
    val colors = rememberColorTheme()

    var searchQuery by remember { mutableStateOf("") }
    var isShowComposeDialog by remember { mutableStateOf(false) }

    val filteredEmails = remember(displayedEmails, searchQuery) {
        if (searchQuery.isBlank()) {
            displayedEmails
        } else {
            displayedEmails.filter {
                it.subject.contains(searchQuery, ignoreCase = true) ||
                        it.body.contains(searchQuery, ignoreCase = true) ||
                        it.sender.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(colors.neutralGray)
                    .border(1.dp, colors.borderGray.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search emails",
                    tint = colors.slateGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search inbox, sender, drafts...",
                            color = colors.slateGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("email_search_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    maxLines = 1
                )
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = colors.slateGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeFolder == "INBOX") colors.primary else colors.surface)
                        .border(
                            width = 1.dp,
                            color = if (activeFolder == "INBOX") Color.Transparent else colors.borderGray,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setEmailFolder("INBOX") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📥 Inbox Folder",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeFolder == "INBOX") Color.White else colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeFolder == "SENT") colors.primary else colors.surface)
                        .border(
                            width = 1.dp,
                            color = if (activeFolder == "SENT") Color.Transparent else colors.borderGray,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setEmailFolder("SENT") }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📤 Sent Hub",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (activeFolder == "SENT") Color.White else colors.textPrimary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (activeFolder) {
                        "INBOX" -> "INBOX CATEGORY"
                        "SENT" -> "OUTBOX LOG"
                        else -> "BIN ARCHIVED"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primary,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "${filteredEmails.size} lines found",
                    fontSize = 11.sp,
                    color = colors.slateGray,
                    fontWeight = FontWeight.Bold
                )
            }

            if (filteredEmails.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Inbox,
                            contentDescription = "Void Mail Box",
                            tint = colors.slateGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your simulated $activeFolder is clear",
                            color = colors.slateGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEmails) { email ->
                        EmailItemRow(email = email, onClick = { viewModel.selectEmail(email) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { isShowComposeDialog = true },
            containerColor = colors.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp)
                .testTag("compose_email_fab")
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "Compose mail")
        }

        selectedEmail?.let { email ->
            EmailDetailsDialog(email = email, onClose = { viewModel.selectEmail(null) })
        }

        if (isShowComposeDialog) {
            ComposeEmailDialog(
                onClose = { isShowComposeDialog = false },
                onSend = { to, subject, body ->
                    viewModel.composeEmailDirectly(to, subject, body)
                    isShowComposeDialog = false
                }
            )
        }
    }
}

@Composable
fun EmailItemRow(email: Email, onClick: () -> Unit) {
    val colors = rememberColorTheme()
    val initial = email.sender.firstOrNull()?.toString() ?: "U"

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (!email.isRead) colors.lightPurple else colors.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (!email.isRead) colors.primary.copy(alpha = 0.25f) else colors.borderGray
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = email.sender,
                        fontWeight = if (!email.isRead) FontWeight.Black else FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = getRelativeTimeString(email.timestamp),
                        fontSize = 11.sp,
                        color = colors.slateGray,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = email.subject,
                    fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Medium,
                    color = if (!email.isRead) colors.primary else colors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Text(
                    text = email.body,
                    fontSize = 12.sp,
                    color = colors.slateGray,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmailDetailsDialog(email: Email, onClose: () -> Unit) {
    val colors = rememberColorTheme()
    val initial = email.sender.firstOrNull()?.toString() ?: "U"

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, colors.borderGray, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EMAIL MESSAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primary,
                        letterSpacing = 1.sp
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(24.dp)
                            .background(colors.neutralGray, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close View",
                            tint = colors.slateGray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = email.subject,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.uppercase(),
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = email.sender,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )
                        Text(
                            text = email.senderEmail,
                            fontSize = 11.sp,
                            color = colors.slateGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.neutralGray)
                        .padding(14.dp)
                ) {
                    Text(
                        text = email.body,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close Portal", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ComposeEmailDialog(
    onClose: () -> Unit,
    onSend: (to: String, subject: String, body: String) -> Unit
) {
    val colors = rememberColorTheme()
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, colors.borderGray, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMPOSE SIMULATED MAIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primary,
                        letterSpacing = 1.sp
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(24.dp)
                            .background(colors.neutralGray, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close View",
                            tint = colors.slateGray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To (Recipient Name)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderGray,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = colors.slateGray,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject Line") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderGray,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = colors.slateGray,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Email Content Body") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderGray,
                        focusedLabelColor = colors.primary,
                        unfocusedLabelColor = colors.slateGray,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) {
                        Text("Cancel", color = colors.slateGray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (to.isNotBlank() && subject.isNotBlank() && body.isNotBlank()) {
                                onSend(to, subject, body)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = to.isNotBlank() && subject.isNotBlank() && body.isNotBlank()
                    ) {
                        Text("Send Mail", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsTabScreen(viewModel: AssistantViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    val colors = rememberColorTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACTION CONTROLLER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${logs.size} active voice logs tracked",
                    fontSize = 12.sp,
                    color = colors.slateGray,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { viewModel.resetMockEmails() },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
                ) {
                    Text("Reset Inbox", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.clearLogHistory() },
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFE63946).copy(0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear track console log",
                        tint = Color(0xFFE63946),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = "Logs Void state",
                        tint = colors.slateGray,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Visual actions and logs console empty",
                        color = colors.slateGray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    ConsoleLogItemRow(log = log)
                }
            }
        }
    }
}

@Composable
fun ConsoleLogItemRow(log: CommandLog) {
    val colors = rememberColorTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderGray),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.slateGray,
                    fontWeight = FontWeight.Bold
                )

                val badgeColor = when (log.detectedAction) {
                    "GMAIL_CHECK" -> colors.primary
                    "GMAIL_SEND" -> colors.secondary
                    else -> colors.slateGray
                }

                val badgeText = if (log.detectedAction == "NONE") "GENERAL_TALK" else log.detectedAction

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "📥 IN: \"${log.commandText}\"",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "💬 OUT: \"${log.responseSpeech}\"",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.slateGray,
                fontWeight = FontWeight.Bold
            )

            if (log.detectedAction != "NONE") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.borderGray, modifier = Modifier.padding(vertical = 4.dp))
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Action status success",
                        tint = colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STATUS: ${log.actionStatus} (Gmail logic invoked)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.primary
                    )
                }
            }
        }
    }
}

fun getRelativeTimeString(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86450000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timeMs))
    }
}

// ==========================================
// CALENDAR INTEGRATION SCREEN COMPONENTS
// ==========================================

@Composable
fun CalendarTabScreen(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val feed by viewModel.calendarFeed.collectAsState()
    val selectedEvent by viewModel.selectedCalendarEvent.collectAsState()
    val colors = rememberColorTheme()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(viewModel.hasCalendarPermission()) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[android.Manifest.permission.READ_CALENDAR] == true &&
                permissions[android.Manifest.permission.WRITE_CALENDAR] == true
        viewModel.refreshNativeEvents()
    }
    
    LaunchedEffect(hasPermission) {
        viewModel.refreshNativeEvents()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasPermission) colors.lightPurple.copy(alpha = 0.2f) else colors.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(
                        1.dp,
                        if (hasPermission) colors.primary.copy(alpha = 0.4f) else colors.borderGray,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasPermission) Icons.Filled.VerifiedUser else Icons.Filled.Shield,
                            contentDescription = "Sync Security Status",
                            tint = if (hasPermission) colors.primary else colors.slateGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasPermission) "Native Calendar Synchronized" else "Calendar Privacy Sandbox (Room)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasPermission) {
                            "Synchronizing with your system calendar store. Background voice commands can directly query, schedule, and remove events here."
                        } else {
                            "Aura supports connecting to your phone's native Google Calendars. Grant permissions below, or schedule in our local privacy-respecting sandbox without sharing calendar data."
                        },
                        fontSize = 12.sp,
                        color = colors.slateGray
                    )
                    
                    if (!hasPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CALENDAR,
                                        android.Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_permissions_button")
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = "Authorize permissions", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Calendar Permissions", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming Schedule",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.primary
                )
                
                Row {
                    IconButton(
                        onClick = { viewModel.resetCalendarEvents() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.lightPurple.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reload calendars",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(colors.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Schedule appointment",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            if (feed.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = "No appointments",
                            modifier = Modifier.size(60.dp),
                            tint = colors.borderGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No scheduled appointments", fontWeight = FontWeight.Bold, color = colors.slateGray)
                        Text("Hold voice trigger to schedule a new meeting!", fontSize = 12.sp, color = colors.slateGray.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(feed) { event ->
                        CalendarItemRow(event, colors) {
                            viewModel.selectCalendarEvent(event)
                        }
                    }
                }
            }
        }
        
        if (showAddDialog) {
            AddCalendarEventDialog(
                colors = colors,
                onDismiss = { showAddDialog = false },
                onAdd = { title, desc, loc, start, end ->
                    viewModel.addCalendarEventDirectly(title, desc, loc, start, end)
                    showAddDialog = false
                }
            )
        }
        
        if (selectedEvent != null) {
            CalendarEventDetailDialog(
                event = selectedEvent!!,
                colors = colors,
                onDismiss = { viewModel.selectCalendarEvent(null) },
                onDelete = {
                    viewModel.deleteCalendarEventDirectly(selectedEvent!!)
                    viewModel.selectCalendarEvent(null)
                }
            )
        }
    }
}

@Composable
fun CalendarItemRow(
    event: CalendarEvent,
    colors: com.example.ui.theme.ColorTheme,
    onClick: () -> Unit
) {
    val dateString = remember(event.startTime) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(event.startTime))
    }
    
    val timeString = remember(event.startTime, event.endTime) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val startStr = sdf.format(Date(event.startTime))
        val endStr = sdf.format(Date(event.endTime))
        "$startStr - $endStr"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("calendar_event_row_${event.title}"),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(55.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.primary)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Location tag icon",
                        tint = colors.slateGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.location.ifBlank { "Virtual Portal" },
                        fontSize = 11.sp,
                        color = colors.slateGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Time slot selection icon",
                        tint = colors.slateGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$dateString at $timeString",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.slateGray
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = if (event.systemEventId != null) Icons.Filled.CloudCircle else Icons.Filled.Storage,
                contentDescription = if (event.systemEventId != null) "Synchronized native" else "Sandbox saved",
                tint = if (event.systemEventId != null) colors.primary else colors.slateGray.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 🌟 ১. ড্রপ-ডাউন ইয়ার সিলেকশন মেনু কম্পোজেবল (আলাদা করে যোগ করা হয়েছে)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearDropdownMenu(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    colors: com.example.ui.theme.ColorTheme
) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val yearsList = remember { (currentYear..currentYear + 10).toList() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("বছর নির্বাচন করুন (Year)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.borderGray,
                focusedLabelColor = colors.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            yearsList.forEach { year ->
                DropdownMenuItem(
                    text = { Text(text = year.toString(), fontWeight = FontWeight.Bold) },
                    onClick = {
                        onYearSelected(year)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AddCalendarEventDialog(
    colors: com.example.ui.theme.ColorTheme,
    onDismiss: () -> Unit,
    onAdd: (title: String, desc: String, loc: String, start: Long, end: Long) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    val startCalendar = remember { Calendar.getInstance() }
    val endCalendar = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) } }

    // 🌟 বছর ট্র্যাক এবং সিঙ্ক করার জন্য স্টেট
    var selectedStartYear by remember { mutableStateOf(startCalendar.get(Calendar.YEAR)) }
    var selectedEndYear by remember { mutableStateOf(endCalendar.get(Calendar.YEAR)) }
    
    var startText by remember {
        mutableStateOf(SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(startCalendar.time))
    }
    var endText by remember {
        mutableStateOf(SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(endCalendar.time))
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Schedule Appointment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_event_title_input")
                )
                
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Event Location") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_event_location_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Meeting Notes") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.borderGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_event_desc_input")
                )

                // 🌟 ২. বছর সিলেক্ট করার জন্য ড্রপডাউন দুটি এখানে বসানো হয়েছে
                Text("তারিখের বছর সেট করুন (Year Dropdown):", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold)

                YearDropdownMenu(
                    selectedYear = selectedStartYear,
                    onYearSelected = { year ->
                        selectedStartYear = year
                        startCalendar.set(Calendar.YEAR, year)
                        startText = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(startCalendar.time)
                    },
                    colors = colors
                )

                YearDropdownMenu(
                    selectedYear = selectedEndYear,
                    onYearSelected = { year ->
                        selectedEndYear = year
                        endCalendar.set(Calendar.YEAR, year)
                        endText = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(endCalendar.time)
                    },
                    colors = colors
                )
                
                // Start selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker(context, startCalendar) { cal ->
                                showTimePicker(context, cal) { finalCal ->
                                    selectedStartYear = finalCal.get(Calendar.YEAR) // ড্রপডাউন সিঙ্ক
                                    startText = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(finalCal.time)
                                }
                            }
                        }
                        .border(1.dp, colors.borderGray, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Start Date & Time", fontSize = 11.sp, color = colors.slateGray)
                        Text(startText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    }
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Pick start time slot",
                        tint = colors.primary
                    )
                }

                // End selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDatePicker(context, endCalendar) { cal ->
                                showTimePicker(context, cal) { finalCal ->
                                    selectedEndYear = finalCal.get(Calendar.YEAR) // ড্রপডাউন সিঙ্ক
                                    endText = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(finalCal.time)
                                }
                            }
                        }
                        .border(1.dp, colors.borderGray, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("End Date & Time", fontSize = 11.sp, color = colors.slateGray)
                        Text(endText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    }
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Pick end time slot",
                        tint = colors.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.slateGray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onAdd(title, description, location, startCalendar.timeInMillis, endCalendar.timeInMillis)
                            } else {
                                Toast.makeText(context, "Please enter an event title", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier = Modifier.testTag("submit_add_event_button")
                    ) {
                        Text("Schedule Appointment")
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarEventDetailDialog(
    event: CalendarEvent,
    colors: com.example.ui.theme.ColorTheme,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(event.startTime) {
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(event.startTime))
    }
    
    val timeString = remember(event.startTime, event.endTime) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val startStr = sdf.format(Date(event.startTime))
        val endStr = sdf.format(Date(event.endTime))
        "$startStr - $endStr"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Appointment Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.primary
                    )
                    Icon(
                        imageVector = if (event.systemEventId != null) Icons.Filled.CloudCircle else Icons.Filled.Storage,
                        contentDescription = "Stored location tracker icon",
                        tint = if (event.systemEventId != null) colors.primary else colors.slateGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                HorizontalDivider(color = colors.borderGray)
                
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = colors.primary
                )
                
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Event location detail",
                        tint = colors.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = event.location.ifBlank { "Virtual Portal / Meet Room" },
                        fontSize = 13.sp,
                        color = colors.slateGray
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Date status view icon",
                        tint = colors.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = dateString, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        Text(text = timeString, fontSize = 12.sp, color = colors.slateGray)
                    }
                }

                if (event.description.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.lightPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("Meeting Notes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.slateGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = event.description,
                            fontSize = 12.sp,
                            color = colors.slateGray
                        )
                    }
                }
                
                if (event.systemEventId != null) {
                    Text(
                        text = "System Calendar Event ID: ${event.systemEventId}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.slateGray.copy(alpha = 0.6f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946)),
                        modifier = Modifier.testTag("delete_event_button")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Event", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// Android OS Picker Hooks for dialog triggers
// ------------------------------------------

fun showDatePicker(context: android.content.Context, calendar: Calendar, onDateSelected: (Calendar) -> Unit) {
    android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            onDateSelected(calendar)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun showTimePicker(context: android.content.Context, calendar: Calendar, onTimeSelected: (Calendar) -> Unit) {
    android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            onTimeSelected(calendar)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    ).show()
}
