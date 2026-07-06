package com.example.happybirthday

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1E1E1E)) {
                    AppScreen()
                }
            }
        }
    }
}

// Data classes
data class AIConfig(val name: String, val apiKey: String, val apiSite: String, val model: String)
data class ChatMessage(val role: String, val content: String)

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("AIConfigs", Context.MODE_PRIVATE)
    var configs by remember { mutableStateOf(loadConfigs(sharedPreferences)) }
    
    // Settings State
    var sName by remember { mutableStateOf("") }
    var sKey by remember { mutableStateOf("") }
    var sSite by remember { mutableStateOf("") }
    var sModel by remember { mutableStateOf("") }
    var extraMode by remember { mutableStateOf(false) }

    // Chat State
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isWaiting by remember { mutableStateOf(false) }
    var currentAI by remember { mutableStateOf<AIConfig?>(null) }
    var chatId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    
    // UI Navigation State
    var screen by remember { mutableStateOf("settings") } // settings, chat, files
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = Color(0xFF1E1E1E),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when(screen) {
                        "chat" -> "ChatGPT Clone"
                        "files" -> "Chat Files"
                        else -> "AI Settings"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
                Row {
                    if (screen == "chat") {
                        IconButton(onClick = { screen = "files" }) {
                            Icon(Icons.Default.Info, contentDescription = "Files", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { screen = if (screen == "settings") "chat" else "settings" }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }
        },
        bottomBar = {
            if (screen == "chat" && currentAI != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message AI...") },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isWaiting) {
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
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF7B61FF))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (screen) {
                "settings" -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("AI Name") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = sKey, onValueChange = { sKey = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = sSite, onValueChange = { sSite = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = sModel, onValueChange = { sModel = it }, label = { Text("Model Name") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = extraMode, onCheckedChange = { extraMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7B61FF)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extra Mode (File Access)", color = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (sName.isNotBlank() && sKey.isNotBlank() && sSite.isNotBlank() && sModel.isNotBlank()) {
                                    val newConfig = AIConfig(sName, sKey, sSite, sModel)
                                    configs = configs + newConfig
                                    saveConfigs(sharedPreferences, configs)
                                    sName = ""; sKey = ""; sSite = ""; sModel = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B61FF))
                        ) { Text("Save AI Configuration") }

                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn {
                            items(configs) { config ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                        currentAI = config
                                        messages = listOf()
                                        chatId = UUID.randomUUID().toString()
                                        screen = "chat"
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(config.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Model: ${config.model}", color = Color.Gray, fontSize = 12.sp)
                                        Text("Tap to Chat", color = Color(0xFF7B61FF), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                "chat" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        state = listState
                    ) {
                        items(messages) { message -> ChatBubble(message) }
                        if (isWaiting) {
                            item { Text("AI is thinking...", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                        }
                    }
                }
                "files" -> {
                    val chatDir = File(context.getExternalFilesDir(null), "AI_Chat_$chatId")
                    val files = chatDir.listFiles()?.toList() ?: emptyList()
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (files.isEmpty()) {
                            item { Text("No files created in this chat yet.", color = Color.Gray) }
                        } else {
                            items(files) { file ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(file.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("${file.length()} bytes", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val bgColor = if (isUser) Color(0xFF7B61FF) else Color(0xFF2C2C2C)
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = alignment
    ) {
        Box(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(text = message.content, color = Color.White)
        }
    }
}

// Network & File Logic
suspend fun sendMessageToAI(context: Context, config: AIConfig, history: List<ChatMessage>, extraMode: Boolean, chatId: String): String = withContext(Dispatchers.IO) {
    try {
        val baseUrl = config.apiSite.trimEnd('/')
        val url = URL("$baseUrl/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            doOutput = true
        }

        val messagesArray = JSONArray()
        
        // System Prompt for Extra Mode
        if (extraMode) {
            val sysPrompt = JSONObject()
            sysPrompt.put("role", "system")
            sysPrompt.put("content", "You have file system access. To create or edit a file, output EXACTLY this format: <file name=\"filename.txt\">file content here</file>. Do not use markdown code blocks for files. Use the exact tag.")
            messagesArray.put(sysPrompt)
        }

        for (msg in history) {
            val jsonMsg = JSONObject()
            jsonMsg.put("role", msg.role)
            jsonMsg.put("content", msg.content)
            messagesArray.put(jsonMsg)
        }

        val payload = JSONObject()
        payload.put("model", config.model)
        payload.put("messages", messagesArray)

        conn.outputStream.use { os -> os.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val rawContent = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            
            // File Parser Logic
            if (extraMode) {
                val regex = Regex("<file name=\"(.*?)\">(.*?)</file>", RegexOption.DOT_MATCHES_ALL)
                val matches = regex.findAll(rawContent).toList()
                if (matches.isNotEmpty()) {
                    val chatDir = File(context.getExternalFilesDir(null), "AI_Chat_$chatId")
                    if (!chatDir.exists()) chatDir.mkdirs()
                    
                    for (match in matches) {
                        val (fileName, fileContent) = match.destructured
                        File(chatDir, fileName.trim()).writeText(fileContent.trim())
                    }
                    // Return text without the file tags so chat looks clean
                    return@withContext regex.replace(rawContent, "[File created successfully]").trim()
                }
            }
            return@withContext rawContent
        } else {
            val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() }
            return@withContext "Error: ${conn.responseCode} - $errorResponse"
        }
    } catch (e: Exception) {
        return@withContext "Failed to connect: ${e.message}"
    }
}

// Helpers
@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF2C2C2C), unfocusedContainerColor = Color(0xFF2C2C2C),
    cursorColor = Color(0xFF7B61FF), focusedBorderColor = Color(0xFF7B61FF),
    unfocusedBorderColor = Color.DarkGray
)

private fun saveConfigs(sharedPreferences: android.content.SharedPreferences, configs: List<AIConfig>) {
    val jsonArray = JSONArray()
    for (config in configs) {
        val jsonObject = JSONObject()
        jsonObject.put("name", config.name); jsonObject.put("apiKey", config.apiKey)
        jsonObject.put("apiSite", config.apiSite); jsonObject.put("model", config.model)
        jsonArray.put(jsonObject)
    }
    sharedPreferences.edit().putString("configs", jsonArray.toString()).apply()
}

private fun loadConfigs(sharedPreferences: android.content.SharedPreferences): List<AIConfig> {
    val configsString = sharedPreferences.getString("configs", null) ?: return emptyList()
    val jsonArray = JSONArray(configsString)
    val configs = mutableListOf<AIConfig>()
    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        configs.add(AIConfig(jsonObject.getString("name"), jsonObject.getString("apiKey"), jsonObject.getString("apiSite"), jsonObject.optString("model", "gpt-3.5-turbo")))
    }
    return configs
}
