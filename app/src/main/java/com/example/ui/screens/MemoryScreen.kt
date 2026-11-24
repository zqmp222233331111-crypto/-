package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.MemoryItem
import com.example.ui.components.GlassCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(
    appLanguage: AppLanguage,
    memories: List<MemoryItem>,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    val isBn = appLanguage == AppLanguage.BENGALI
    var factInput by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("ব্যক্তিগত") }
    var confirmClearAll by remember { mutableStateOf(false) }

    val categories = if (isBn) {
        listOf("ব্যক্তিগত", "নাম ও পরিচয়", "পছন্দ", "চ্যানেল ও কাজ")
    } else {
        listOf("Personal", "Identity", "Preferences", "Creator Work")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("memory_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isBn) "পেছনে" else "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = if (isBn) "শাইলার স্মৃতি (Memory)" else "Shaila Memory",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isBn) "প্রতিটি উত্তরে এই তথ্যগুলো মনে রাখবে" else "Sent automatically with every conversation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (memories.isNotEmpty()) {
                TextButton(onClick = { confirmClearAll = true }) {
                    Text(
                        text = if (isBn) "সব মুছুন" else "Clear All",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
        ) {
            Text(
                text = if (isBn) "নতুন তথ্য যোগ করুন" else "Add New Memory",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = factInput,
                onValueChange = { factInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_fact_input"),
                placeholder = {
                    Text(
                        if (isBn) "যেমন: আমার নাম আমির, আমি টেক ভিডিও বানাই" else "e.g., My name is Amir, I make tech videos"
                    )
                },
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    if (factInput.isNotBlank()) {
                        onAddMemory(factInput, selectedCategory)
                        factInput = ""
                    }
                },
                enabled = factInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("memory_add_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBn) "স্মৃতিতে সংরক্ষণ করুন" else "Save Memory")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (memories.isEmpty()) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isBn) "এখনো কোনো স্মৃতি সংরক্ষণ করা হয়নি" else "No memories saved yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isBn) "আপনার নাম, ভাষা বা কাজের ধরন যোগ করলে শাইলা আরও ব্যক্তিগত উত্তর দেবে।" else "Add your name, tone, or preferences so Shaila remembers them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(memories, key = { it.id }) { item ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.fact,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            IconButton(
                                onClick = { onDeleteMemory(item.id) },
                                modifier = Modifier.testTag("delete_memory_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = if (isBn) "মুছুন" else "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(if (isBn) "সব স্মৃতি মুছবেন?" else "Clear All Memories?") },
            text = {
                Text(
                    if (isBn) "সংরক্ষিত সব স্মৃতি স্থায়ীভাবে মুছে ফেলা হবে। নিশ্চিত?" else "All saved memories will be permanently deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearAll = false
                        onClearAll()
                    }
                ) {
                    Text(if (isBn) "হ্যাঁ, মুছুন" else "Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClearAll = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}
