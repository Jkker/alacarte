package dev.alacarte.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.highcapable.yukihookapi.hook.factory.prefs
import dev.alacarte.data.ConfigRepository
import dev.alacarte.data.LogRepository
import dev.alacarte.data.MenuConfig
import dev.alacarte.data.MenuItemConfig
import dev.alacarte.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val titles = listOf("Config", "Logs")
    val context = LocalContext.current

    // Global Log Receiver registered here to persist across tab switches
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    val msg = intent?.getStringExtra("msg") ?: return
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    LogRepository.addLog("[$timestamp] $msg")
                }
            }
        val filter = IntentFilter("dev.alacarte.LOG_BROADCAST")
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            if (index == 0) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                            }
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                ConfigScreen()
            } else {
                LogScreen()
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun ConfigScreen() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ConfigRepository.getConfig(context.prefs())) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun save(newConfig: MenuConfig) {
        config = newConfig
        ConfigRepository.saveConfig(context, newConfig)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Debug Logging",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Switch(
                        checked = config.isDebug,
                        onCheckedChange = { save(config.copy(isDebug = it)) },
                    )
                }

                Text(
                    "Menu Items Order",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            itemsIndexed(config.items) { index, item ->
                MenuItemCard(
                    item = item,
                    onMoveUp =
                        if (index > 0) {
                            {
                                val newList = config.items.toMutableList()
                                java.util.Collections.swap(newList, index, index - 1)
                                save(config.copy(items = newList))
                            }
                        } else {
                            null
                        },
                    onMoveDown =
                        if (index < config.items.size - 1) {
                            {
                                val newList = config.items.toMutableList()
                                java.util.Collections.swap(newList, index, index + 1)
                                save(config.copy(items = newList))
                            }
                        } else {
                            null
                        },
                    onDelete = {
                        val newList = config.items.toMutableList()
                        newList.removeAt(index)
                        save(config.copy(items = newList))
                    },
                    onUpdate = { updatedItem ->
                        val newList = config.items.toMutableList()
                        newList[index] = updatedItem
                        save(config.copy(items = newList))
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    "Hidden Items",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            items(config.hiddenItems) { hiddenItem ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(hiddenItem, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val newList = config.hiddenItems.toMutableList()
                            newList.remove(hiddenItem)
                            save(config.copy(hiddenItems = newList))
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { key, label, isHidden, packageName ->
                if (isHidden) {
                    val newList = config.hiddenItems.toMutableList()
                    if (!newList.contains(key)) newList.add(key)
                    save(config.copy(hiddenItems = newList))
                } else {
                    val newList = config.items.toMutableList()
                    newList.add(MenuItemConfig(key, label, packageName = packageName))
                    save(config.copy(items = newList))
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MenuItemCard(
    item: MenuItemConfig,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
    onUpdate: (MenuItemConfig) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Key: ${item.key}", style = MaterialTheme.typography.labelSmall)
                    if (item.packageName != null) {
                        Text(
                            "Pkg: ${item.packageName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = item.customLabel,
                        onValueChange = { onUpdate(item.copy(customLabel = it)) },
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column {
                    IconButton(onClick = onMoveUp ?: {}, enabled = onMoveUp != null) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
                    }
                    IconButton(onClick = onMoveDown ?: {}, enabled = onMoveDown != null) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enabled")
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = { onUpdate(item.copy(isEnabled = it)) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun AddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Boolean, String?) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var isHiddenType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Original Title (Key)") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isHiddenType, onCheckedChange = { isHiddenType = it })
                    Text("Add to Hidden List")
                }

                if (!isHiddenType) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Custom Label") },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package Name (Optional)") },
                        singleLine = true,
                        placeholder = { Text("e.g. com.google.android.apps.translate") },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        key,
                        label.ifBlank { key },
                        isHiddenType,
                        packageName.ifBlank { null },
                    )
                },
                enabled = key.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun LogScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when logs update
    LaunchedEffect(LogRepository.logs.size) {
        if (LogRepository.logs.isNotEmpty()) {
            listState.animateScrollToItem(LogRepository.logs.size - 1)
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallFloatingActionButton(
                    onClick = {
                        val allLogs = LogRepository.logs.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(allLogs))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Copy Logs")
                }

                FloatingActionButton(onClick = { LogRepository.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                }
            }
        },
    ) { padding ->
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(LogRepository.logs) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
