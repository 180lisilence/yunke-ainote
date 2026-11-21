package com.yunke.ainote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunke.ainote.data.NoteEntity
import com.yunke.ainote.settings.SettingsStore
import com.yunke.ainote.ui.ChatScreen
import com.yunke.ainote.ui.ChatViewModel
import com.yunke.ainote.ui.NoteEditScreen
import com.yunke.ainote.ui.NoteListScreen
import com.yunke.ainote.ui.NoteViewModel
import com.yunke.ainote.ui.PlaceholderScreen
import com.yunke.ainote.ui.SettingsScreen
import com.yunke.ainote.ui.TrashScreen
import com.yunke.ainote.ui.nav.MainTab
import com.yunke.ainote.ui.theme.ThemeMode
import com.yunke.ainote.ui.theme.YunKeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon

class MainActivity : ComponentActivity() {

    private val vm: NoteViewModel by viewModels()
    private val chatVm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { SettingsStore(baseContext) }
            var themeMode by rememberSaveable {
                mutableStateOf(runCatching { ThemeMode.valueOf(store.themeMode) }.getOrDefault(ThemeMode.SYSTEM))
            }
            YunKeTheme(mode = themeMode) {
                var tab by remember { mutableStateOf(MainTab.Notes) }
                var editingNew by remember { mutableStateOf(false) }
                var editTarget by remember { mutableStateOf<NoteEntity?>(null) }

                val notes by vm.notes.collectAsStateWithLifecycle()
                val trash by vm.trash.collectAsStateWithLifecycle()
                val chatMessages by chatVm.messages.collectAsStateWithLifecycle()
                val modelState by chatVm.modelState.collectAsStateWithLifecycle()
                val models by chatVm.models.collectAsStateWithLifecycle()
                val currentModel by chatVm.currentModel.collectAsStateWithLifecycle()
                val channel by chatVm.channel.collectAsStateWithLifecycle()
                val remoteConfig by chatVm.remoteConfig.collectAsStateWithLifecycle()
                val chatParams by chatVm.params.collectAsStateWithLifecycle()
                val usage by chatVm.usage.collectAsStateWithLifecycle()
                val builtinStatus by chatVm.builtinStatus.collectAsStateWithLifecycle()
                val builtinProgress by chatVm.builtinProgress.collectAsStateWithLifecycle()
                val lastError by chatVm.lastError.collectAsStateWithLifecycle()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            MainTab.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = { tab = item },
                                    label = { Text(item.label) },
                                    icon = { Icon(iconFor(item), contentDescription = item.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            MainTab.Notes -> NoteListScreen(
                                notes = notes,
                                onNew = {
                                    editingNew = true
                                    editTarget = null
                                },
                                onOpen = {
                                    editingNew = false
                                    editTarget = it
                                },
                                onDelete = { vm.delete(it) },
                                onOpenChat = { tab = MainTab.Chat },
                            )

                            MainTab.Chat -> ChatScreen(
                                messages = chatMessages,
                                channel = channel,
                                onChannelChange = { chatVm.setChannel(it) },
                                modelState = modelState,
                                models = models,
                                currentModel = currentModel,
                                builtinStatus = builtinStatus,
                                builtinProgress = builtinProgress,
                                lastError = lastError,
                                onLoadModel = { chatVm.loadModel(it) },
                                onReleaseModel = { chatVm.releaseModel() },
                                remoteConfig = remoteConfig,
                                onSaveRemoteConfig = { chatVm.saveRemoteConfig(it) },
                                params = chatParams,
                                onSaveParams = { chatVm.saveChatParams(it) },
                                usage = usage,
                                onClearUsage = { chatVm.clearUsage() },
                                onBack = { tab = MainTab.Notes },
                                onSend = { chatVm.send(it) },
                            )

                            MainTab.Folders -> PlaceholderScreen(
                                title = "文件夹",
                                subtitle = "文件夹层级与分类将在后续批次接入",
                            )

                            MainTab.Trash -> TrashScreen(
                                trash = trash,
                                onRestore = { vm.restore(it) },
                                onPurge = { vm.permanentDelete(it) },
                            )

                            MainTab.Settings -> SettingsScreen(
                                mode = themeMode,
                                onModeChange = {
                                    themeMode = it
                                    store.themeMode = it.name
                                },
                            )
                        }
                    }
                }

                if (editingNew || editTarget != null) {
                    val note = editTarget
                    val revs = if (note != null) {
                        vm.revisions(note.id)
                            .collectAsStateWithLifecycle(initialValue = emptyList()).value
                    } else {
                        emptyList()
                    }
                    NoteEditScreen(
                        existing = note,
                        revisions = revs,
                        onBack = {
                            editingNew = false
                            editTarget = null
                        },
                        onSave = { type, title, body, itemsJson, existing ->
                            vm.saveTyped(type, title, body, itemsJson, existing)
                            editingNew = false
                            editTarget = null
                        },
                        onDelete = {
                            vm.delete(it)
                            editingNew = false
                            editTarget = null
                        },
                    )
                }
            }
        }
    }

    private fun iconFor(tab: MainTab) = when (tab) {
        MainTab.Notes -> Icons.Outlined.List
        MainTab.Chat -> Icons.Outlined.Chat
        MainTab.Folders -> Icons.Outlined.Folder
        MainTab.Trash -> Icons.Outlined.Delete
        MainTab.Settings -> Icons.Outlined.Settings
    }
}
