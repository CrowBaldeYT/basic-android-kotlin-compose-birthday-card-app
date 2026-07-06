package com.example.happybirthday

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1E1E1E)) {
                    AppScreen(this)
                }
            }
        }
    }
}

// Data classes
data class AIConfig(val name: String, val apiKey: String, val apiSite: String, val model: String)
data class ChatMessage(val role: String, val content: String)

@Composable
fun AppScreen(context: Context) {
    val sharedPreferences = context.getSharedPreferences("AIConfigs", Context.MODE_PRIVATE)
    var configs by remember { mutableStateOf(loadConfigs(sharedPreferences)) }
    
    // Input states for Settings
    var sName by remember { mutableStateOf("") }
    var sKey by remember { mutableStateOf("") }
    var sSite by remember { mutableStateOf("") }
    var sModel by remember { mutableStateOf("") }

    // Chat states
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isWaiting by remember { mutableStateOf(false) }
    var currentAI by remember { mutableStateOf<AIConfig?>(null) }
    var showSettings by remember { mutableStateOf(currentAI == null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showSettings) "AI Settings" else "ChatGPT Clone",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(Icons.Default.Settings, contentDescription = "Toggle", tint = Color.White)
                }
            }

            if (showSettings) {
                // --- SETTINGS SCREEN ---
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("AI Name (e.g., OpenAI)") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = sKey, onValueChange = { sKey = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = sSite, onValueChange = { sSite = it }, label = { Text("API Base URL (e.g., https://api.openai.com)") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = sModel, onValueChange = { sModel = it }, label = { Text("Model Name (e.g., gpt-3.5-turbo)") }, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
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
                    Text("Saved AIs:", color = Color.White)
                    LazyColumn {
                        items(configs) { config ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    currentAI = config
                                    messages = listOf()
                                    showSettings = false
                                },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(config.name, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text("Model: ${config.model}", color = Color.Gray, fontSize = 12.sp)
                                    Text("Tap to Chat", color = Color(0xFF7B61FF), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // --- CHAT SCREEN ---
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState
                ) {
                    items(messages) { message ->
                        ChatBubble(message)
                    }
                    if (isWaiting) {
                        item { Text("AI is thinking...", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                    }
                }
                
                // Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                            if (inputText.isNotBlank() && currentAI != null && !isWaiting) {
                                val userMsg = inputText
                                messages = messages + ChatMessage("user", userMsg)
                                inputText = ""
                                isWaiting = true
                                
                                scope.launch {
                                    val aiResponse = sendMessageToAI(currentAI!!, messages)
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

// Network function to call AI API
suspend fun sendMessageToAI(config: AIConfig, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
    try {
        // Ensure URL doesn't end with / before appending the endpoint
        val baseUrl = config.apiSite.trimEnd('/')
        val url = URL("$baseUrl/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            doOutput = true
        }

        // Create JSON payload
        val messagesArray = JSONArray()
        for (msg in history) {
            val jsonMsg = JSONObject()
            jsonMsg.put("role", msg.role)
            jsonMsg.put("content", msg.content)
            messagesArray.put(jsonMsg)
        }

        val payload = JSONObject()
        payload.put("model", config.model)
        payload.put("messages", messagesArray)

        // Send data
        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        // Read response
        val responseCode = conn.responseCode
        if (responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val choices = jsonResponse.getJSONArray("choices")
            return@withContext choices.getJSONObject(0).getJSONObject("message").getString("content")
        } else {
            val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() }
            return@withContext "Error: $responseCode - $errorResponse"
        }
    } catch (e: Exception) {
        return@withContext "Failed to connect: ${e.message}"
    }
}

// Helper UI Functions
@Composable
fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF2C2C2C),
    unfocusedContainerColor = Color(0xFF2C2C2C),
    cursorColor = Color(0xFF7B61FF),
    focusedBorderColor = Color(0xFF7B61FF),
    unfocusedBorderColor = Color.DarkGray
)

private fun saveConfigs(sharedPreferences: android.content.SharedPreferences, configs: List<AIConfig>) {
    val jsonArray = JSONArray()
    for (config in configs) {
        val jsonObject = JSONObject()
        jsonObject.put("name", config.name)
        jsonObject.put("apiKey", config.apiKey)
        jsonObject.put("apiSite", config.apiSite)
        jsonObject.put("model", config.model)
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
        configs.add(
            AIConfig(
                name = jsonObject.getString("name"),
                apiKey = jsonObject.getString("apiKey"),
                apiSite = jsonObject.getString("apiSite"),
                model = jsonObject.optString("model", "gpt-3.5-turbo")
            )
        )
    }
    return configs
}
