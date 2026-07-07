package com.example.happybirthday

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
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

// --- Colors ---
val BgColor = Color(0xFF0E0E11)
val CardColor = Color(0xFF1A1A1F)
val AccentPurple = Color(0xFF7C5CFF)
val AccentPurpleDark = Color(0xFF5C3CFF)
val TextWhite = Color(0xFFEAEAEA)
val TextGray = Color(0xFF8E8E93)

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
    
    // Navigation State
    var screen by remember { mutableStateOf(if (configs.isEmpty()) "settings" else "models") }
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
                onBack = { screen = "models" }
            )
            "models" -> ModelsScreen(
                configs = configs, 
                currentAI = currentAI, 
                onSelect = { config ->
                    currentAI = config
                    messages = listOf()
                    chatId = UUID.randomUUID().toString()
                    screen = "chat"
                },
                onAdd = { screen = "settings" }
            )
            "chat" -> ChatScreen(
                currentAI = currentAI, 
                messages = messages, 
                inputText = inputText, 
                isWaiting = isWaiting, 
                listState = listState,
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
                onBack = { screen = "models" },
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
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = BgColor,
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp, 48.dp, 24.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, tint = TextWhite, contentDescription = "Back") }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("AI Configuration", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Manage your AI providers", color = TextGray, fontSize = 14.sp)
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            item {
                // Form Card
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Provider Details", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                        
                        PremiumTextField("Provider Name", sName, onNameChange, Icons.Rounded.Person)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PremiumTextField("API Key", sKey, onKeyChange, Icons.Rounded.Lock, 
                            isPassword = !showPassword, 
                            trailing = {
                                TextButton(onClick = onTogglePassword) {
                                    Text(if (showPassword) "Hide" else "Show", color = AccentPurple)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PremiumTextField("Base URL", sSite, onSiteChange, Icons.Rounded.Info)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PremiumTextField("Model Name", sModel, onModelChange, Icons.Rounded.Info)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Extra Mode Toggle
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Extra Mode (File Access)", color = TextWhite, fontWeight = FontWeight.SemiBold)
                            Text("Allows AI to create and edit files", color = TextGray, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(checked = extraMode, onCheckedChange = { onToggleExtra() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentPurple))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Save Button
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), ambientColor = AccentPurple, spotColor = AccentPurple)) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(modifier = Modifier.background(Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark))).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Save Configuration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text("Saved Models", color = TextGray, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// --- MODELS SCREEN ---
@Composable
fun ModelsScreen(configs: List<AIConfig>, currentAI: AIConfig?, onSelect: (AIConfig) -> Unit, onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.padding(24.dp, 48.dp, 24.dp, 24.dp)) {
            Text("My AI Models", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(configs) { config ->
                    val isSelected = currentAI?.name == config.name
                    val glowColor = if (isSelected) AccentPurple else Color.Transparent
                    
                    Box(modifier = Modifier.padding(vertical = 6.dp).shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = glowColor, spotColor = glowColor)) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(config) },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardColor)
                        ) {
                            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark))), contentAlignment = Alignment.Center) {
                                    Text(config.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(config.name, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    Text("Online", color = Color(0xFF4CD964), fontSize = 12.sp)
                                    Text(config.model, color = TextGray, fontSize = 12.sp)
                                }
                                Icon(Icons.Rounded.Star, tint = TextGray, contentDescription = "Select")
                            }
                        }
                    }
                }
            }
        }
        
        // FAB
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).shadow(12.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple)) {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = AccentPurple,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, tint = Color.White, contentDescription = "Add")
            }
        }
    }
}

// --- CHAT SCREEN ---
@Composable
fun ChatScreen(
    currentAI: AIConfig?, messages: List<ChatMessage>, inputText: String, isWaiting: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit, onSend: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp, 48.dp, 24.dp, 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, tint = TextWhite, contentDescription = "Back") }
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark))), contentAlignment = Alignment.Center) {
                    Text(currentAI?.name?.take(1)?.uppercase() ?: "AI", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chat", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(currentAI?.model ?: "Unknown", color = TextGray, fontSize = 12.sp)
                }
                IconButton(onClick = { /* Search */ }) { Icon(Icons.Rounded.Search, tint = TextGray, contentDescription = "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tint = TextGray, contentDescription = "Settings") }
            }

            // Chat List
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 16.dp)
            ) {
                items(messages) { message -> ChatBubble(message, currentAI?.name?.take(1) ?: "A") }
                if (isWaiting) {
                    item { LoadingDots() }
                }
            }

            // Input Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(CardColor).border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { /* Attach */ }) { Icon(Icons.Rounded.Add, tint = TextGray, contentDescription = "Add") }
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            modifier = Modifier.weight(1f).padding(0.dp, 12.dp),
                            textStyle = TextStyle(color = TextWhite, fontSize = 15.sp),
                            singleLine = false,
                            cursorBrush = Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Send Button
                Box(modifier = Modifier.shadow(8.dp, CircleShape, ambientColor = AccentPurple, spotColor = AccentPurple)) {
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark)))
                    ) {
                        Icon(Icons.Rounded.Send, tint = Color.White, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

// --- CHAT BUBBLE ---
@Composable
fun ChatBubble(message: ChatMessage, aiInitial: String) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isUser) Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark)) else Brush.linearGradient(listOf(CardColor, CardColor))
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = alignment
    ) {
        if (!isUser) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentPurple, AccentPurpleDark))), contentAlignment = Alignment.Center) {
                Text(aiInitial, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, if (isUser) 20.dp else 4.dp))
                .background(bubbleColor)
                .padding(14.dp)
        ) {
            Text(message.content, color = TextWhite, fontSize = 15.sp)
        }
    }
}

// --- LOADING ANIMATION (Pulsating Circle) ---
@Composable
fun LoadingDots() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse)
    )
    
    Row(
