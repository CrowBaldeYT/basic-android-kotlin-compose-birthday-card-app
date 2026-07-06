package com.example.happybirthday

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(this)
                }
            }
        }
    }
}

// Data class to hold our AI configuration
data class AIConfig(
    val name: String,
    val apiKey: String,
    val apiSite: String
)

@Composable
fun AppScreen(context: Context) {
    val sharedPreferences = context.getSharedPreferences("AIConfigs", Context.MODE_PRIVATE)
    
    // State variables
    var configs by remember { mutableStateOf(loadConfigs(sharedPreferences)) }
    var name by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiSite by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Add New AI", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Input fields
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("AI Name (e.g., OpenAI, Claude)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key / Token") },
            visualTransformation = PasswordVisualTransformation(), // Hides the key
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiSite,
            onValueChange = { apiSite = it },
            label = { Text("API Site / Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (name.isNotBlank() && apiKey.isNotBlank() && apiSite.isNotBlank()) {
                    val newConfig = AIConfig(name, apiKey, apiSite)
                    configs = configs + newConfig
                    saveConfigs(sharedPreferences, configs)
                    // Clear fields
                    name = ""
                    apiKey = ""
                    apiSite = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save AI Configuration")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Saved AIs", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // List of saved configurations
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(configs) { config ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = config.name, style = MaterialTheme.typography.titleMedium)
                        Text(text = "Site: ${config.apiSite}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Key: ••••••••", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// Helper function to save configs to local storage
private fun saveConfigs(sharedPreferences: android.content.SharedPreferences, configs: List<AIConfig>) {
    val jsonArray = JSONArray()
    for (config in configs) {
        val jsonObject = JSONObject()
        jsonObject.put("name", config.name)
        jsonObject.put("apiKey", config.apiKey)
        jsonObject.put("apiSite", config.apiSite)
        jsonArray.put(jsonObject)
    }
    sharedPreferences.edit().putString("configs", jsonArray.toString()).apply()
}

// Helper function to load configs from local storage
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
                apiSite = jsonObject.getString("apiSite")
            )
        )
    }
    return configs
}
