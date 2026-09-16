package com.allsocial.sealclone

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ================= DATA MODELS =================
data class SearchItem(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: String,
    val thumbnail: String,
    val url: String
)

data class ActiveDownloadTask(
    val id: String,
    val title: String,
    val progress: Float,
    val speed: String = ""
)

data class DownloadedRecord(
    val title: String,
    val filePath: String,
    val thumbnail: String,
    val quality: String,
    val ext: String,
    val fileSize: String
)

enum class AppTab(val label: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    TASKS("Tasks", Icons.Filled.Downloading, Icons.Outlined.Downloading),
    DOWNLOADS("Downloads", Icons.Filled.DownloadDone, Icons.Outlined.DownloadDone),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

// ================= VIEWMODEL =================
class SealViewModel @JvmOverloads constructor(application: Application? = null) : ViewModel() {
    private val prefs = application?.getSharedPreferences("seal_app_prefs", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs?.getBoolean("pref_dark_mode", true) ?: true)
    val isDarkMode = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs?.edit()?.putBoolean("pref_dark_mode", enabled)?.apply()
    }

    fun toggleDarkMode() {
        setDarkMode(!_isDarkMode.value)
    }

    private val _useDynamicColor = MutableStateFlow(
        prefs?.getBoolean("pref_dynamic_color", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 
            ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    )
    val useDynamicColor = _useDynamicColor.asStateFlow()

    fun setUseDynamicColor(enabled: Boolean) {
        _useDynamicColor.value = enabled
        prefs?.edit()?.putBoolean("pref_dynamic_color", enabled)?.apply()
    }

    private val _colorPreset = MutableStateFlow(prefs?.getString("pref_color_preset", "CYAN") ?: "CYAN")
    val colorPreset = _colorPreset.asStateFlow()

    fun setColorPreset(preset: String) {
        _colorPreset.value = preset
        prefs?.edit()?.putString("pref_color_preset", preset)?.apply()
    }

    private val _activeTasks = MutableStateFlow<Map<String, ActiveDownloadTask>>(emptyMap())
    val activeTasks = _activeTasks.asStateFlow()

    private val _downloadedHistory = MutableStateFlow<List<DownloadedRecord>>(emptyList())
    val downloadedHistory = _downloadedHistory.asStateFlow()

    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet = _showBottomSheet.asStateFlow()

    private val _selectedItemForSheet = MutableStateFlow<SearchItem?>(null)
    val selectedItemForSheet = _selectedItemForSheet.asStateFlow()

    init {
        viewModelScope.launch {
            DownloadForegroundService.activeTasksFlow.collect { tasks ->
                _activeTasks.value = tasks
            }
        }
        viewModelScope.launch {
            DownloadForegroundService.downloadEvents.collect { event ->
                if (event.isSuccess && event.record != null) {
                    addCompletedRecord(event.record)
                }
            }
        }
    }

    fun cancelDownload(context: Context, taskId: String) {
        DownloadForegroundService.cancelDownload(context, taskId)
    }

    fun toggleBottomSheet() {
        _showBottomSheet.value = !_showBottomSheet.value
    }

    fun setShowBottomSheet(show: Boolean) {
        _showBottomSheet.value = show
    }

    fun openBottomSheet(item: SearchItem) {
        _selectedItemForSheet.value = item
        _showBottomSheet.value = true
    }

    fun closeBottomSheet() {
        _showBottomSheet.value = false
    }

    fun updateProgress(id: String, title: String, progress: Float, speed: String) {
        val current = _activeTasks.value.toMutableMap()
        if (progress >= 100f) {
            current.remove(id)
        } else {
            current[id] = ActiveDownloadTask(id, title, progress, speed)
        }
        _activeTasks.value = current
    }

    fun addCompletedRecord(record: DownloadedRecord) {
        _downloadedHistory.value = listOf(record) + _downloadedHistory.value
    }

    fun removeRecord(record: DownloadedRecord) {
        val file = File(record.filePath)
        if (file.exists()) file.delete()
        _downloadedHistory.value = _downloadedHistory.value.filter { it != record }
    }
}

// VideoInfo extension properties for DownloaderEngine compatibility
private val videoInfoEntriesMap = java.util.WeakHashMap<VideoInfo, List<VideoInfo>>()

var VideoInfo.entries: List<VideoInfo>
    get() = videoInfoEntriesMap[this] ?: emptyList()
    set(value) {
        if (value.isNotEmpty()) videoInfoEntriesMap[this] = value else videoInfoEntriesMap.remove(this)
    }

val VideoInfo.channel: String?
    get() = uploader

val VideoInfo.durationString: String?
    get() {
        val d = duration
        return if (d > 0) {
            val m = d / 60
            val s = d % 60
            "%02d:%02d".format(m, s)
        } else {
            "00:00"
        }
    }

// Helper function jo safely instance ensure karegi
suspend fun ensureYoutubeDLInitialized(context: Context): Boolean = withContext(Dispatchers.IO) {
    EngineInitState.ensureInitialized(context)
}

// ================= DOWNLOAD ENGINE =================
object DownloaderBridge {
    fun ensureInit(context: Context) {
        EngineInitState.ensureInitialized(context)
    }

    fun getDownloadDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun fixUrl(input: String): String = DownloaderEngine.fixUrl(input)

    suspend fun searchTop10(context: Context, query: String): List<SearchItem> = withContext(Dispatchers.IO) {
        ensureInit(context)
        DownloaderEngine.search(query)
    }

    suspend fun executeDownload(
        context: Context,
        targetUrl: String,
        formatSpec: String,
        isAudioOnly: Boolean,
        audioBitrate: String? = null,
        processId: String? = null,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        val validUrl = fixUrl(targetUrl)
        val downloadDir = getDownloadDir(context)
        val existingFiles = downloadDir.listFiles()?.toSet() ?: emptySet()
        val outPattern = "${downloadDir.absolutePath}/%(title).100B.%(ext)s"

        val request = YoutubeDLRequest(validUrl).apply {
            addOption("--no-warnings")
            addOption("--no-check-certificate")
            addOption("--prefer-free-formats")
            addOption("--extractor-args", "youtube:player_client=ios,android")
            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", audioBitrate ?: "320K")
                addOption("-f", "bestaudio/best")
            } else {
                val res = formatSpec.toIntOrNull()
                if (res != null) {
                    addOption("-f", "bestvideo[height<=$res]+bestaudio/best[height<=$res]/best")
                } else if (formatSpec.isNotBlank()) {
                    addOption("-f", "$formatSpec+bestaudio/bestvideo+bestaudio/best[ext=mp4]/best")
                } else {
                    addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                }
                addOption("--merge-output-format", "mp4")
            }
            addOption("-o", outPattern)
            addOption("--no-mtime")
        }

        YoutubeDL.getInstance().execute(request, processId) { p, _, line ->
            onProgress(p, line ?: "")
        }

        val allFiles = downloadDir.listFiles()?.toSet() ?: emptySet()
        val newlyCreated = allFiles - existingFiles
        val targetFile = newlyCreated.maxByOrNull { it.lastModified() }
            ?: downloadDir.listFiles()?.maxByOrNull { it.lastModified() }
            ?: File(downloadDir, "media.mp4")

        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(if (isAudioOnly) "audio/mpeg" else "video/mp4"),
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        targetFile
    }
}

// ================= ACTIVITY =================
class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf("")
    private var requestedTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureIntentUrl(intent)

        setContent {
            val vm: SealViewModel = viewModel()
            val isDarkMode by vm.isDarkMode.collectAsState()
            val useDynamicColor by vm.useDynamicColor.collectAsState()
            val colorPresetId by vm.colorPreset.collectAsState()
            val context = LocalContext.current

            val colorScheme = remember(isDarkMode, useDynamicColor, colorPresetId) {
                if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    val preset = ColorPresetRegistry.getPreset(colorPresetId)
                    ColorPresetRegistry.buildColorScheme(preset, isDarkMode)
                }
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                MainAppScaffold(
                    initialSharedUrl = sharedUrl,
                    onUrlConsumed = { sharedUrl = "" },
                    requestedTab = requestedTab,
                    onTabConsumed = { requestedTab = null },
                    vm = vm
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureIntentUrl(intent)
    }

    private fun captureIntentUrl(intent: Intent?) {
        val targetTab = intent?.getStringExtra("OPEN_TAB")
        if (!targetTab.isNullOrEmpty()) {
            requestedTab = targetTab
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val incoming = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val found = Regex("""(https?://[^\s]+)""").find(incoming)?.value
            if (!found.isNullOrEmpty()) {
                sharedUrl = found.trim()
            }
        }
    }
}

// ================= MAIN SCAFFOLD & TABS =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    initialSharedUrl: String,
    onUrlConsumed: () -> Unit,
    requestedTab: String? = null,
    onTabConsumed: () -> Unit = {},
    vm: SealViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    val activeTasks by vm.activeTasks.collectAsState()
    val downloadedHistory by vm.downloadedHistory.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(requestedTab) {
        when (requestedTab) {
            "TASKS" -> selectedTab = AppTab.TASKS
            "DOWNLOADS" -> selectedTab = AppTab.DOWNLOADS
            "HOME" -> selectedTab = AppTab.HOME
        }
        if (requestedTab != null) {
            onTabConsumed()
        }
    }

    // Request Storage, Media, and Notification Permissions
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                context,
                "Permissions recommended for saving downloaded media & notifications",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        val missing = perms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            FloatingAppNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                activeTasksCount = activeTasks.size
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                AppTab.HOME -> HomeSearchScreen(
                    incomingUrl = initialSharedUrl,
                    onUrlHandled = onUrlConsumed,
                    onStartDownload = { url, format, isAudio, qualityLabel, title, thumb ->
                        // Check WRITE_EXTERNAL_STORAGE on Android <= 9 (API 28)
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            val writePerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                            if (writePerm != PackageManager.PERMISSION_GRANTED) {
                                permissionsLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            }
                        }

                        val taskId = System.currentTimeMillis().toString()
                        DownloadForegroundService.enqueueDownload(
                            context = context,
                            taskId = taskId,
                            url = url,
                            format = format,
                            isAudio = isAudio,
                            qualityLabel = qualityLabel,
                            title = title,
                            thumb = thumb
                        )
                        Toast.makeText(context, "Download started in foreground service", Toast.LENGTH_SHORT).show()
                    },
                    vm = vm
                )
                AppTab.TASKS -> TasksListScreen(
                    tasks = activeTasks.values.toList(),
                    onCancelTask = { taskId -> vm.cancelDownload(context, taskId) }
                )
                AppTab.DOWNLOADS -> DownloadsScreen(
                    vm = vm,
                    onNavigateHome = { selectedTab = AppTab.HOME }
                )
                AppTab.SETTINGS -> SettingsScreen(vm = vm)
            }
        }
    }
}

// ================= 1. HOME & 10-SEARCH SCREEN =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSearchScreen(
    incomingUrl: String,
    onUrlHandled: () -> Unit,
    onStartDownload: (url: String, format: String, isAudio: Boolean, qualityLabel: String, title: String, thumb: String) -> Unit,
    vm: SealViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val showBottomSheet by vm.showBottomSheet.collectAsState()
    val selectedItem by vm.selectedItemForSheet.collectAsState()

    var searchInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isUpdatingEngine by remember { mutableStateOf(false) }
    var currentYtDlpVersion by remember { mutableStateOf("Checking...") }
    var showColorPresetsSheet by remember { mutableStateOf(false) }

    // Screen load hote hi safe init
    LaunchedEffect(Unit) {
        val ver = EngineInitState.getOrFetchVersion(context)
        currentYtDlpVersion = ver
    }

    // Update Dialog with "Update Now" button
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUpdatingEngine) showUpdateDialog = false },
            title = {
                Text(
                    text = "yt-dlp Engine Update",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Current Version: $currentYtDlpVersion",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Update yt-dlp config engine to latest version to fix broken extractors and bot detection limits.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (isUpdatingEngine) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Updating engine binaries...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isUpdatingEngine = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val isReady = EngineInitState.ensureInitialized(context)
                                if (!isReady) {
                                    throw IllegalStateException("Failed to load native binaries.")
                                }

                                // Update execute karein
                                val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
                                val newVer = EngineInitState.getOrFetchVersion(context, forceRefresh = true)
                                EngineInitState.setUpdatedVersion(newVer)
                                
                                withContext(Dispatchers.Main) {
                                    currentYtDlpVersion = newVer
                                    isUpdatingEngine = false
                                    showUpdateDialog = false
                                    Toast.makeText(context, "Engine Updated to $newVer", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isUpdatingEngine = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    enabled = !isUpdatingEngine,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ADB5))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false },
                    enabled = !isUpdatingEngine
                ) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl.isNotBlank()) {
            searchInput = incomingUrl
            onUrlHandled()
            isLoading = true
            scope.launch {
                searchResults = DownloaderEngine.searchOrFetch(incomingUrl)
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // App Bar Title & Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Seal Downloader",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Update Config Button
                FilledTonalButton(
                    onClick = { showUpdateDialog = true },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.testTag("btn_update_config")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Update Config",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Update Config",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Color Presets & Theme Palette Button
                IconButton(
                    onClick = { showColorPresetsSheet = true },
                    modifier = Modifier.testTag("btn_theme_palette")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme and Color Presets",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                val isDarkMode by vm.isDarkMode.collectAsState()
                IconButton(
                    onClick = { vm.toggleDarkMode() },
                    modifier = Modifier.testTag("btn_quick_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        if (!showBottomSheet && selectedItem == null) {
                            val inputUrl = searchInput.trim()
                            if (inputUrl.isNotBlank()) {
                                vm.openBottomSheet(
                                    SearchItem(
                                        id = System.currentTimeMillis().toString(),
                                        title = inputUrl,
                                        uploader = "Direct Media",
                                        duration = "--:--",
                                        thumbnail = "",
                                        url = inputUrl
                                    )
                                )
                            } else if (searchResults.isNotEmpty()) {
                                vm.openBottomSheet(searchResults.first())
                            } else {
                                vm.toggleBottomSheet()
                            }
                        } else {
                            vm.toggleBottomSheet()
                        }
                    },
                    modifier = Modifier.testTag("btn_toggle_bottom_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Toggle download options bottom sheet",
                        tint = if (showBottomSheet) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }

        // Small Style Search URL Box
        SmallSearchUrlBox(
            value = searchInput,
            onValueChange = { searchInput = it },
            onSearch = {
                if (searchInput.isNotBlank()) {
                    keyboardController?.hide() // Keyboard band karein
                    isLoading = true
                    scope.launch {
                        searchResults = DownloaderEngine.searchOrFetch(searchInput.trim())
                        isLoading = false
                        if (searchResults.isEmpty()) {
                            Toast.makeText(context, "No results found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onPaste = {
                clipboard.getText()?.let { clipData ->
                    val raw = clipData.text.toString().trim()
                    val urlRegex = Regex("""(https?://[^\s]+)""")
                    val fullUrl = urlRegex.find(raw)?.value ?: raw
                    searchInput = fullUrl

                    // Auto trigger search on paste
                    if (fullUrl.isNotBlank()) {
                        keyboardController?.hide()
                        isLoading = true
                        scope.launch {
                            searchResults = DownloaderEngine.searchOrFetch(fullUrl)
                            isLoading = false
                        }
                    }
                }
            },
            onClear = { searchInput = "" }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // All Social Media Platform Icons (niche all social media icon add)
        SocialMediaPlatformsBar(
            onPlatformClick = { platform ->
                val clipData = clipboard.getText()?.text?.toString()?.trim() ?: ""
                val urlRegex = Regex("""(https?://[^\s]+)""")
                val detectedUrl = urlRegex.find(clipData)?.value ?: ""
                if (detectedUrl.isNotBlank() && platform.domainKeywords.any { detectedUrl.contains(it, ignoreCase = true) }) {
                    searchInput = detectedUrl
                    keyboardController?.hide()
                    Toast.makeText(context, "Pasted ${platform.name} link!", Toast.LENGTH_SHORT).show()
                    isLoading = true
                    scope.launch {
                        searchResults = DownloaderEngine.searchOrFetch(detectedUrl)
                        isLoading = false
                    }
                } else if (detectedUrl.isNotBlank()) {
                    searchInput = detectedUrl
                    keyboardController?.hide()
                    Toast.makeText(context, "Pasted link from clipboard!", Toast.LENGTH_SHORT).show()
                    isLoading = true
                    scope.launch {
                        searchResults = DownloaderEngine.searchOrFetch(detectedUrl)
                        isLoading = false
                    }
                } else {
                    Toast.makeText(context, "${platform.name}: ${platform.sampleTip}", Toast.LENGTH_LONG).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // In-App Player Card
        AnimatedVisibility(visible = playingUrl != null) {
            playingUrl?.let { playStream ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        StreamPlayer(url = playStream)
                        IconButton(
                            onClick = { playingUrl = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Full scrollable 10 Results List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Isse list keyboard ke upar smooth scroll hogi
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { vm.openBottomSheet(item) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(110.dp, 68.dp).clip(RoundedCornerShape(12.dp))) {
                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = item.duration,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.uploader,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = { vm.openBottomSheet(item) },
                        modifier = Modifier.testTag("btn_item_download_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Open download options",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (searchResults.isEmpty() && !isLoading && playingUrl == null) {
            Spacer(modifier = Modifier.height(8.dp))
            SocialMediaCapabilitiesCard()
        }
    }

    // Material 3 Bottom Sheet for Stream Preview & Multi-Format Downloading
    if (showBottomSheet) {
        val targetItem = selectedItem ?: SearchItem(
            id = "direct_url",
            title = if (searchInput.isNotBlank()) searchInput.trim() else "Direct Download",
            uploader = "Media Downloader",
            duration = "--:--",
            thumbnail = "",
            url = if (searchInput.isNotBlank()) searchInput.trim() else ""
        )

        MediaDownloadBottomSheet(
            item = targetItem,
            onDismissRequest = { vm.closeBottomSheet() },
            onPlayStream = { url ->
                playingUrl = url
            },
            onDownloadAudio = { url, bitrate, title, thumb ->
                onStartDownload(url, "ba/b", true, bitrate, title, thumb)
            },
            onDownloadVideo = { url, resolution, formatSelector, title, thumb ->
                onStartDownload(url, formatSelector, false, resolution, title, thumb)
            },
            vm = vm
        )
    }

    // Color Presets & Dynamic Theme Bottom Sheet
    if (showColorPresetsSheet) {
        val useDynamicColor by vm.useDynamicColor.collectAsState()
        val colorPresetId by vm.colorPreset.collectAsState()
        val isDarkMode by vm.isDarkMode.collectAsState()

        ColorPresetsSheet(
            currentPresetId = colorPresetId,
            useDynamicColor = useDynamicColor,
            isDark = isDarkMode,
            onSelectPreset = { vm.setColorPreset(it) },
            onToggleDynamicColor = { vm.setUseDynamicColor(it) },
            onToggleDarkMode = { vm.setDarkMode(it) },
            onDismissRequest = { showColorPresetsSheet = false }
        )
    }
}

// ================= 2. ACTIVE TASKS SCREEN =================
@Composable
fun TasksListScreen(
    tasks: List<ActiveDownloadTask>,
    onCancelTask: (String) -> Unit = {}
) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Downloading,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No downloads running",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Active downloads are managed by the Foreground Service and will show real-time progress here and in your notification shade even when backgrounded.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(tasks, key = { it.id }) { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("task_card_${task.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            task.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCancelTask(task.id) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("cancel_task_${task.id}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel download",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${task.progress.toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(task.speed.ifBlank { "Downloading..." }, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ================= 3. LIBRARY & HISTORY SCREEN =================
@Composable
fun LibraryHistoryScreen(
    records: List<DownloadedRecord>,
    onDelete: (DownloadedRecord) -> Unit,
    onOpenFile: (DownloadedRecord) -> Unit
) {
    if (records.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloads in library", color = Color.Gray, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        items(records) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOpenFile(item) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.size(65.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${item.quality} • ${item.ext.uppercase()} • ${item.fileSize}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(0.8f))
                    }
                }
            }
        }
    }
}

// ================= PLAYER COMPOSABLE =================
@Composable
fun StreamPlayer(url: String) {
    val context = LocalContext.current
    var hasError by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    hasError = true
                }
            })
            try {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                playWhenReady = true
            } catch (e: Exception) {
                hasError = true
            }
        }
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Unable to stream video preview directly",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            "Open In External Player / Browser",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ================= 4. SETTINGS SCREEN =================
@Composable
fun SettingsScreen(
    vm: SealViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by vm.isDarkMode.collectAsState()
    val useDynamicColor by vm.useDynamicColor.collectAsState()
    val colorPresetId by vm.colorPreset.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen")
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Appearance / Theme Section Card
        Text(
            text = "Appearance",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("theme_settings_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Quick Toggle Row with Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleDarkMode() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "Theme Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Dark Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDarkMode) "Dark theme enabled" else "Light theme enabled",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { vm.setDarkMode(it) },
                        modifier = Modifier.testTag("theme_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Theme Mode Selector Option Cards
                Text(
                    text = "Theme Preference",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Light Theme Option
                    OutlinedCard(
                        onClick = { vm.setDarkMode(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_light"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (!isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (!isDarkMode) 2.dp else 1.dp,
                            color = if (!isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = "Light Mode",
                                tint = if (!isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Light",
                                fontSize = 13.sp,
                                fontWeight = if (!isDarkMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Dark Theme Option
                    OutlinedCard(
                        onClick = { vm.setDarkMode(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_dark"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isDarkMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isDarkMode) 2.dp else 1.dp,
                            color = if (isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = "Dark Mode",
                                tint = if (isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Dark",
                                fontSize = 13.sp,
                                fontWeight = if (isDarkMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isDarkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Dynamic Color Option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic Color (Material You)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Derives palette from Android wallpaper" else "Requires Android 12+",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = { vm.setUseDynamicColor(it) },
                            modifier = Modifier.testTag("settings_switch_dynamic_color")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Accent Color Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_color_presets_row")
                ) {
                    items(ColorPresetRegistry.presets) { preset ->
                        val isSelected = !useDynamicColor && colorPresetId.equals(preset.id, ignoreCase = true)
                        Surface(
                            onClick = {
                                vm.setUseDynamicColor(false)
                                vm.setColorPreset(preset.id)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) preset.previewColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) preset.previewColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("settings_preset_${preset.id.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(preset.previewColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    preset.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Downloader Preferences Section
        Text(
            text = "Downloader Preferences",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsInfoRow(title = "Download Location", subtitle = "App Scoped Storage / Downloads")
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsInfoRow(title = "Aria2c Multi-connection", subtitle = "Enabled (Fast segmented downloading)")
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsInfoRow(title = "Audio Extraction Quality", subtitle = "MP3 (320kbps High fidelity)")
            }
        }

        // About Section
        Text(
            text = "About & Core Engine",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var ytdlpVersion by remember { mutableStateOf("Checking...") }
        var isUpdatingEngineInSettings by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val ver = EngineInitState.getOrFetchVersion(context)
            ytdlpVersion = ver
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsInfoRow(title = "App Version", subtitle = "1.0.0 (Release Build)")
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                SettingsInfoRow(title = "Core Binaries", subtitle = "yt-dlp ($ytdlpVersion) • FFmpeg • Aria2c")
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "yt-dlp Engine Update",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Download latest extractors to bypass bot verification",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            isUpdatingEngineInSettings = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val isReady = EngineInitState.ensureInitialized(context)
                                    if (!isReady) {
                                        throw IllegalStateException("Failed to load native binaries.")
                                    }
                                    val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
                                    val newVer = EngineInitState.getOrFetchVersion(context, forceRefresh = true)
                                    EngineInitState.setUpdatedVersion(newVer)
                                    withContext(Dispatchers.Main) {
                                        ytdlpVersion = newVer
                                        isUpdatingEngineInSettings = false
                                        Toast.makeText(context, "Engine Updated to $newVer", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isUpdatingEngineInSettings = false
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(50),
                        enabled = !isUpdatingEngineInSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_settings_update_ytdlp")
                    ) {
                        if (isUpdatingEngineInSettings) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
