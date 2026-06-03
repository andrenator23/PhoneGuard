package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.CameraHelper
import com.example.viewmodel.AppScreen
import com.example.viewmodel.PhoneGuardViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.window.DialogProperties
import com.example.data.IntruderLog

class MainActivity : ComponentActivity() {

    private lateinit var cameraHelper: CameraHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraHelper = CameraHelper(applicationContext)

        setContent {
            MyApplicationTheme {
                val viewModel: PhoneGuardViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // Request camera permission launcher
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasCameraPermission = isGranted
                    if (isGranted) {
                        Toast.makeText(context, "Camera permission granted. Watch out, snoopers!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Camera permission denied. PhoneGuard needs camera to snap intruders.", Toast.LENGTH_LONG).show()
                    }
                }

                // Check permission on startup
                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                // Sync immersive full screen behavior depending on active screen
                LaunchedEffect(currentScreen) {
                    setImmersiveMode(currentScreen == AppScreen.TRAP)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (currentScreen) {
                            AppScreen.TRAP -> {
                                TrapScreen(
                                    viewModel = viewModel,
                                    cameraHelper = cameraHelper,
                                    hasPermission = hasCameraPermission,
                                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                                )
                            }
                            AppScreen.DASHBOARD -> {
                                DashboardScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    viewModel = viewModel,
                                    cameraHelper = cameraHelper,
                                    hasPermission = hasCameraPermission
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setImmersiveMode(enable: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enable) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.shutdown()
    }
}

// ------------------- TRAP SCREEN UI -------------------

@Composable
fun TrapScreen(
    viewModel: PhoneGuardViewModel,
    cameraHelper: CameraHelper,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val trapState by viewModel.trapState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Bind CameraX on Trap start
    var isCameraBound by remember { mutableStateOf(false) }
    var surfaceProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }

    LaunchedEffect(hasPermission, surfaceProvider) {
        if (hasPermission && surfaceProvider != null) {
            cameraHelper.bindCamera(lifecycleOwner, surfaceProvider) {
                isCameraBound = true
            }
        }
    }

    val launcherApps = remember {
        listOf(
            ShortcutApp("Secure Vault", Icons.Default.Lock, Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFFF1744)))),
            ShortcutApp("Messenger", Icons.Default.Email, Brush.linearGradient(listOf(Color(0xFF00B0FF), Color(0xFF2979FF)))),
            ShortcutApp("Google Photo", Icons.Default.Person, Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00C853)))),
            ShortcutApp("Settings", Icons.Default.Settings, Brush.linearGradient(listOf(Color(0xFF90A4AE), Color(0xFF455A64)))),
            ShortcutApp("Secret Notes", Icons.Default.Warning, Brush.linearGradient(listOf(Color(0xFFFFD600), Color(0xFFFFAB00)))),
            ShortcutApp("Play Store", Icons.Default.PlayArrow, Brush.linearGradient(listOf(Color(0xFFAA00FF), Color(0xFFD500F9))))
        )
    }

    // Capture Trigger lambda
    val triggerAction: (String) -> Unit = { appName ->
        if (!hasPermission) {
            onRequestPermission()
        } else {
            viewModel.triggerTrap(appName) { onSuccess, onError ->
                cameraHelper.takePhoto(onSuccess, onError)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate Dark Wallpaper Base
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        // Invisible 1dp SurfaceProvider for robust camera focus/exposure stabilization
        AndroidView(
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .align(Alignment.TopStart),
            update = { previewView ->
                if (surfaceProvider == null) {
                    surfaceProvider = previewView.surfaceProvider
                }
            }
        )

        // Desktop Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
        ) {
            // Fake Status Bar Row (to completely simulate real Android interface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PhoneGuard LTE",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "98%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Dynamic Real-time Clock Widget
            CenteredClockWidget()

            Spacer(modifier = Modifier.height(28.dp))

            // Fake temptative Messenger Notification banner (Highly tempting bait widget!)
            FakeNotificationBait(onTap = { triggerAction("Messenger Banner") })

            Spacer(modifier = Modifier.height(32.dp))

            // App shortcuts Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(launcherApps) { app ->
                    ShortcutItem(
                        app = app,
                        onTap = { triggerAction(app.name) }
                    )
                }
            }

            // Prompt tip showing ONLY if permission is missing (so owner knows)
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onRequestPermission() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Colors.RedOrange)
                        Text(
                            text = "Warning: Camera Permission required. Tap here to grant.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Drawer / Navigation Pill indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .width(134.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }

        // Triple-Tap Invisible Guard Area at the bottom of the wallpaper to exit
        var tapCount by remember { mutableStateOf(0) }
        var lastTapTime by remember { mutableStateOf(0L) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 500) {
                        tapCount++
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now
                    if (tapCount >= 3) {
                        tapCount = 0
                        viewModel.setScreen(AppScreen.DASHBOARD)
                    }
                }
        )

        // Overlays when trap triggers
        if (trapState.showFakeLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = false) {}, // Intercept clicks during camera freeze
                contentAlignment = Alignment.Center
            ) {
                // Invisible/transparent loading state to delay the snooper
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Custom Fake Unresponsive Crash Dialog (Perfect decoy completion!)
        if (trapState.showFakeCrashDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCrashDialog() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissCrashDialog() }) {
                        Text("Close app", color = Color(0xFF007BFF))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissCrashDialog() }) {
                        Text("Wait", color = Color(0xFF007BFF))
                    }
                },
                title = {
                    Text(
                        text = "${trapState.triggeredApp ?: "System UI"} keeps stopping",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = Color.Black
                    )
                },
                text = {
                    Text(
                        text = "Send feedback to help prevent this issue in the future.",
                        fontSize = 14.sp,
                        color = Color(0xFF555555)
                    )
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }
    }
}

@Composable
fun CenteredClockWidget() {
    val sdfClock = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val sdfDate = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    var timeString by remember { mutableStateOf(sdfClock.format(Date())) }
    var dateString by remember { mutableStateOf(sdfDate.format(Date())) }

    // Update clock every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            timeString = sdfClock.format(Date())
            dateString = sdfDate.format(Date())
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeString,
            fontSize = 76.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            letterSpacing = (-2).sp,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = dateString,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun FakeNotificationBait(onTap: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Fake Messenger Icon with alert red badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0084FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                // Red badge dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .align(Alignment.TopEnd)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Messenger • Sarah",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "now",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hey! Is this your phone? I left my keys at your place...",
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun ShortcutItem(
    app: ShortcutApp,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(app.backgroundBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = app.icon,
                contentDescription = app.name,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.name,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

data class ShortcutApp(
    val name: String,
    val icon: ImageVector,
    val backgroundBrush: Brush
)

// ------------------- OWNER DASHBOARD SCREEN UI -------------------

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: PhoneGuardViewModel,
    cameraHelper: CameraHelper,
    hasPermission: Boolean
) {
    val context = LocalContext.current
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }
    var isTestCapturing by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Dashboard Title Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PhoneGuard",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Owner Dashboard & Security Log",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.setScreen(AppScreen.TRAP) },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Arm System",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info Card & Gestures Manual
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYSTEM STATUS: ACTIVE & ARMED",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "How to use PhoneGuard:\n" +
                                "1. Tap the Lock button above to arms the immersive Trap screen.\n" +
                                "2. Snoopers will see a realistic launcher. Tapping ANY app triggers camera capture.\n" +
                                "3. TRIPLE-TAP the bottom empty area of the wallpaper to return to this dashboard.\n",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live quick trigger actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Action Button to Arm Trap
                Button(
                    onClick = { viewModel.setScreen(AppScreen.TRAP) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Arm Safe Trap")
                }

                // Quick photo-test trigger
                OutlinedButton(
                    onClick = {
                        if (!hasPermission) {
                            Toast.makeText(context, "Camera permission needed!", Toast.LENGTH_SHORT).show()
                        } else {
                            isTestCapturing = true
                            cameraHelper.takePhoto(
                                onSuccess = { path ->
                                    isTestCapturing = false
                                    viewModel.triggerTrap("Dashboard QuickTest") { onSuccess, _ ->
                                        onSuccess(path)
                                    }
                                    Toast.makeText(context, "Test image saved successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { _ ->
                                    isTestCapturing = false
                                    Toast.makeText(context, "Test camera capture failed.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    enabled = !isTestCapturing
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTestCapturing) "Snapping..." else "Test Capture")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logs Segment title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intruder Logs (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (logs.isNotEmpty()) {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = { viewModel.clearAllLogs() }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }
            }

            // Scrollable Logs list
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "All Clear",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No intruder attempts logged.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Your phone is guarded and completely safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs) { log ->
                        IntruderLogItem(
                            log = log,
                            onPhotoTap = { previewPhotoPath = log.filePath },
                            onDelete = { viewModel.deleteLog(log) }
                        )
                    }
                }
            }
        }

        // Full Screen Viewer Modal
        previewPhotoPath?.let { path ->
            FullscreenPhotoViewer(
                filePath = path,
                onDismiss = { previewPhotoPath = null }
            )
        }
    }
}

@Composable
fun IntruderLogItem(
    log: IntruderLog,
    onPhotoTap: () -> Unit,
    onDelete: () -> Unit
) {
    val formattedTime = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm:ss a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item")
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Decaying Thumbnail of Captured Snooper photo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .clickable { onPhotoTap() },
                contentAlignment = Alignment.Center
            ) {
                val imgFile = File(log.filePath)
                if (imgFile.exists()) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = "Intruder photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "File missing",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Intruder Alert!",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Triggered: ${log.triggeredApp}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete log"
                )
            }
        }
    }
}

@Composable
fun FullscreenPhotoViewer(
    filePath: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Colors.RedOrange)
                Text("Snooper Face Capture", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            val imgFile = File(filePath)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (imgFile.exists()) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = "Full Intruder Face",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Image file not found.",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

object Colors {
    val RedOrange = Color(0xFFFF5722)
}
