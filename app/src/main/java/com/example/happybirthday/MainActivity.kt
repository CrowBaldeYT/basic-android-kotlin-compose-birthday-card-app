package com.example.happybirthday

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PremiumApp()
        }
    }
}

// --- Data Classes ---
data class AIConfig(val name: String, val apiKey: String, val apiSite: String, val model: String)
data class ChatMessage(val role: String, val content: String)

// --- Premium Colors ---
val BgColor = Color(0xFF0B0B0F)
val SecondaryBg = Color(0xFF13131A)
val CardColor = Color(0xFF191923)
val ElevatedColor = Color(0xFF22222D)
val AccentPurple = Color(0xFF7C5CFF)
val SecondaryPurple = Color(0xFFA78BFA)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA1A1AA)
val InactiveColor = Color(0xFF6B7280)
val SuccessColor = Color(0xFF22C55E)

@Composable
fun PremiumApp() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("AIConfigs", Context.MODE_PRIVATE)
    var configs by remember { mutableStateOf(loadConfigs(sharedPreferences)) }
    
    // Settings State
    var sName by remember { mutableStateOf("") }
    var sKey by remember { mutableStateOf("") }
    var sSite by remember { mutableStateOf("") }
    var sModel by remember { mutableStateOf("") }
    var extraMode by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    // Chat State
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isWaiting by remember { mutableStateOf(false) }
    var currentAI by remember { mutableStateOf<AIConfig?>(null) }
    var chatId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var selectedChip by remember { mutableStateOf(0) }
    
    // Navigation State
    var screen by remember { mutableStateOf(if (configs.isEmpty()) "settings" else "chat") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        when (screen) {
            "settings" -> SettingsScreen(
                sName, sKey, sSite, sModel, showPassword, extraMode,
                onNameChange = { sName = it }, 
                onKeyChange = { sKey = it }, 
                onSiteChange = { sSite = it }, 
                onModelChange = { sModel = it }, 
                onTogglePassword = { showPassword = !showPassword }, 
                onToggleExtra = { extraMode = !extraMode },
                onSave = {
                    if (sName.isNotBlank() && sKey.isNotBlank() && sSite.isNotBlank() && sModel.isNotBlank()) {
                        val newConfig = AIConfig(sName, sKey, sSite, sModel)
                        configs = configs + newConfig
                        saveConfigs(sharedPreferences, configs)
                        sName = ""; sKey = ""; sSite = ""; sModel = ""
                    }
                },
                onBack = { screen = "chat" },
                currentAI = currentAI
            )
            "chat" -> ChatScreen(
                currentAI = currentAI, 
                messages = messages, 
                inputText = inputText, 
                isWaiting = isWaiting, 
                listState = listState,
                selectedChip = selectedChip,
                onChipChange = { selectedChip = it },
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() && !isWaiting && currentAI != null) {
                        val userMsg = inputText
                        messages = messages + ChatMessage("user", userMsg)
                        inputText = ""
                        isWaiting = true
                        scope.launch {
                            val aiResponse = sendMessageToAI(context, currentAI!!, messages, extraMode, chatId)
                            isWaiting = false
                            messages = messages + ChatMessage("assistant", aiResponse)
                        }
                    }
                },
                onBack = { screen = "settings" },
                onSettings = { screen = "settings" }
            )
        }
    }
}

// --- SETTINGS SCREEN ---
@Composable
fun SettingsScreen(
    sName: String, sKey: String, sSite: String, sModel: String, showPassword: Boolean, extraMode: Boolean,
    onNameChange: (String) -> Unit, onKeyChange: (String) -> Unit, onSiteChange: (String) -> Unit, onModelChange: (String) -> Unit,
    onTogglePassword: () -> Unit, onToggleExtra: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    currentAI: AIConfig?
) {
    Scaffold(
        containerColor = BgColor,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 48.dp, 24.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, tint = TextPrimary, contentDescription = "Back") }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("AI Providers", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Manage your connected AI models.", color = TextSecondary, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            // Provider Cards Section
            item {
                Text("Connected Models", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = AccentPurple.copy(alpha=0.05f), spotColor = AccentPurple.copy(alpha=0.05f)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, SecondaryPurple))), contentAlignment = Alignment.Center) {
                                Text("AI", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default AI", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(currentAI?.model ?: "No model selected", color = TextSecondary, fontSize = 13.sp)
                            }
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Info, tint = InactiveColor, contentDescription = "Edit")
                        }
                        HorizontalDivider(color = ElevatedColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ElevatedColor), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, tint = TextSecondary, contentDescription = "Add")
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Add New Provider", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Form Card
            item {
                Text("Provider Details", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        PremiumTextField("Provider Name", sName, onNameChange, Icons.Default.Person)
                        Spacer(modifier = Modifier.height(16.dp))
                        PremiumTextField("API Key", sKey, onKeyChange, Icons.Default.Lock, 
                            isPassword = !showPassword, 
                            trailing = {
                                TextButton(onClick = onTogglePassword) {
                                    Text(if (showPassword) "Hide" else "Show", color = AccentPurple)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PremiumTextField("Base URL", sSite, onSiteChange, Icons.Default.Info)
                        Spacer(modifier = Modifier.height(16.dp))
                        PremiumTextField("Model Name", sModel, onModelChange, Icons.Default.Info)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Extra Mode Toggle
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Extra Mode (File Access)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Allows AI to create and edit files", color = TextSecondary, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(checked = extraMode, onCheckedChange = { onToggleExtra() }, colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = AccentPurple))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), ambientColor = AccentPurple, spotColor = AccentPurple)) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(modifier = Modifier.background(Brush.linearGradient(listOf(AccentPurple, SecondaryPurple))).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Save Configuration", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- CHAT SCREEN ---
@Composable
fun ChatScreen(
    currentAI: AIConfig?, messages: List<ChatMessage>, inputText: String, isWaiting: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedChip: Int, onChipChange: (Int) -> Unit,
    onInputChange: (String) -> Unit, onSend: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit
) {
    val chips = listOf("GPT-5", "Claude", "Gemini", "DeepSeek", "GLM", "Llama")
    
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Premium Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp, 48.dp, 24.dp, 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, SecondaryPurple))), contentAlignment = Alignment.Center) {
                    Text(currentAI?.name?.take(1)?.uppercase() ?: "AI", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chat Assistant", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(currentAI?.model ?: "Select a model", color = TextSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = { /* Search */ }) { Icon(Icons.Default.Search, tint = TextSecondary, contentDescription = "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, tint = TextSecondary, contentDescription = "Settings") }
            }

            // Model Selector Chips
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips.size) { index ->
                    val isSelected = selectedChip == index
                    val bgColor = if (isSelected) Brush.linearGradient(listOf(AccentPurple, SecondaryPurple)) else Brush.linearGradient(listOf(CardColor, CardColor))
                    val glow = if (isSelected) Modifier.shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = AccentPurple, spotColor = AccentPurple) else Modifier
                    Box(modifier = glow.then(Modifier.clip(RoundedCornerShape(16.dp)).background(bgColor).clickable { onChipChange(index) }.padding(horizontal = 16.dp, vertical = 8.dp))) {
                        Text(chips[index], color = if (isSelected) TextPrimary else TextSecondary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // Chat List or Empty State
            if (messages.isEmpty()) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(64.dp).shadow(16.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple).clip(CircleShape).background(BgColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Info, tint = AccentPurple, contentDescription = "AI", modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("How can I help today?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ask anything. Code, write, learn, create.", color = TextSecondary, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(24.dp, 0.dp, 24.dp, 16.dp)
                ) {
                    items(messages) { message -> ChatBubble(message, currentAI?.name?.take(1) ?: "A") }
                    if (isWaiting) {
                        item { LoadingDots() }
                    }
                }
            }

            // Floating Glass Input Dock
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(CardColor).border(1.dp, ElevatedColor, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { /* Attach */ }) { Icon(Icons.Default.Add, tint = TextSecondary, contentDescription = "Add") }
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            modifier = Modifier.weight(1f).padding(0.dp, 12.dp),
                            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                            singleLine = false,
                            cursorBrush = Brush.linearGradient(listOf(AccentPurple, SecondaryPurple))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Glowing Send Button
                Box(modifier = Modifier.shadow(12.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple)) {
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, SecondaryPurple)))
                    ) {
                        Icon(Icons.Default.Send, tint = TextPrimary, contentDescription = "Send")
                
