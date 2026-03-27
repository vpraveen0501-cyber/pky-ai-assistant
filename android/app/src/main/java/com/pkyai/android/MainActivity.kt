package com.pkyai.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pkyai.android.ui.*
import com.pkyai.android.ui.theme.*
import com.pkyai.android.voice.VoiceService
import androidx.activity.viewModels
import com.pkyai.android.data.repository.ConfigRepository
import com.pkyai.android.data.repository.DataRepository
import javax.inject.Inject
import okhttp3.OkHttpClient
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sqrt

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Voice : Screen("voice", "Voice", Icons.Default.Mic)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Archive : Screen("archive", "Archive", Icons.Default.Archive)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Privacy : Screen("privacy", "Privacy", Icons.Default.Settings)
}

@AndroidEntryPoint
class MainActivity : FragmentActivity(), SensorEventListener {

    private val mainViewModel: MainViewModel by viewModels()
    private val voiceViewModel: VoiceViewModel by viewModels()
    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var dataRepository: DataRepository
    @Inject lateinit var okHttpClient: OkHttpClient
    private lateinit var voiceService: VoiceService
    private lateinit var authManager: AuthManager
    private var hasAudioPermission by mutableStateOf(false)
    private var isConnected by mutableStateOf(true)

    // Sensor for Shake-to-Wake
    private lateinit var sensorManager: SensorManager
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasAudioPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager(this)

        // Shake-to-Wake Init
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        // Initial Security: Biometric Auth
        val biometricManager = AppBiometricManager(this)
        if (biometricManager.canAuthenticate()) {
            biometricManager.showBiometricPrompt(
                "PKY AI Assistant Security",
                "Authenticate to access your personal AI",
                onSuccess = {
                    mainViewModel.checkAuth()
                },
                onError = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    // Don't finish — allow login via credentials instead
                    mainViewModel.checkAuth()
                }
            )
        } else {
            // If biometric not available, proceed to auth check
            mainViewModel.checkAuth()
        }

        hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            val currentAuthState by mainViewModel.authState.collectAsState()
            val loginError by mainViewModel.loginError.collectAsState()
            val isLoggingIn by mainViewModel.isLoggingIn.collectAsState()

            // M-4: Apply time-based theme immediately and refresh every 15 minutes
            LaunchedEffect(Unit) {
                PkyAiThemeState.updateThemeByTime()
                while (true) {
                    kotlinx.coroutines.delay(15 * 60 * 1000L)
                    PkyAiThemeState.updateThemeByTime()
                }
            }

            PkyAiTheme {
                when (currentAuthState) {
                    is AuthState.Loading -> SecurityLoadingScreen()
                    is AuthState.Authenticated -> MainContent()
                    is AuthState.Unauthenticated -> LoginScreen(
                        isLoggingIn = isLoggingIn,
                        errorMessage = loginError,
                        onLogin = { email, password ->
                            mainViewModel.login(email, password)
                        }
                    )
                }
            }

            LaunchedEffect(currentAuthState) {
                if (currentAuthState is AuthState.Authenticated) {
                    initializeServices()
                }
            }
        }
    }

    private fun initializeServices() {
        val token = authManager.getToken()
        if (token != null) {
            setupVoiceService(token)
        }
    }

    private fun setupVoiceService(token: String) {
        val backendUrl = "${configRepository.getWsBaseUrl()}/ws/voice"
        voiceService = VoiceService(applicationContext, backendUrl, okHttpClient, token) { message ->
            voiceViewModel.updateStatus(message)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainContent() {
        val navController = rememberNavController()
        val items = listOf(Screen.Dashboard, Screen.Voice, Screen.Archive, Screen.Settings)
        val context = androidx.compose.ui.platform.LocalContext.current

        LaunchedEffect(mainViewModel) {
            mainViewModel.globalAlerts.collect { alert ->
                Toast.makeText(context, alert, Toast.LENGTH_SHORT).show()
            }
        }

        var showConsole by remember { mutableStateOf(false) }

        if (showConsole) {
            DeveloperConsoleDialog(dataRepository, onDismiss = { showConsole = false })
        }

        Scaffold(
            topBar = {
                if (!isConnected) {
                    Box(modifier = Modifier.fillMaxWidth().background(Color.Red).padding(4.dp), contentAlignment = Alignment.Center) {
                        Text("Offline - Attempting to Reconnect...", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                if (currentRoute != Screen.Privacy.route) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(Color(0x990F172A))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(9999.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                IconButton(
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier
                                        .background(
                                            if (selected) PkyAiPrimaryDim.copy(alpha = 0.2f) else Color.Transparent,
                                            CircleShape
                                        )
                                        .clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.label,
                                        tint = if (selected) PkyAiPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(navController, startDestination = Screen.Voice.route, modifier = Modifier.padding(innerPadding)) {
                composable(Screen.Voice.route) {
                    val statusText by voiceViewModel.statusText.collectAsState()
                    val selectedModel by voiceViewModel.selectedModel.collectAsState()
                    val isRecordingActive by voiceViewModel.isRecording.collectAsState()
                    VoiceScreen(
                        isConnected = isConnected,
                        hasPermission = hasAudioPermission,
                        statusText = statusText,
                        isRecordingActive = isRecordingActive,
                        selectedModel = selectedModel,
                        availableModels = voiceViewModel.availableModels,
                        onModelSelected = { voiceViewModel.setModel(it) },
                        onStartRecording = {
                            if (::voiceService.isInitialized) {
                                voiceService.setModel(voiceViewModel.selectedModel.value)
                                voiceViewModel.setRecordingState(true)
                                voiceService.startRecording()
                            } else {
                                // L-4: Do NOT set recordingState=true when service isn't ready
                                Toast.makeText(this@MainActivity, "Voice Service initializing...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onStopRecording = {
                            if (::voiceService.isInitialized) {
                                voiceViewModel.setRecordingState(false)
                                voiceService.stopRecordingAndSend()
                            }
                        },
                        onShowConsole = { showConsole = true }
                    )
                }
                composable(Screen.Dashboard.route) { DashboardScreen() }
                composable(Screen.Archive.route) { HistoryScreen() }
                composable(Screen.Settings.route) {
                    SettingsScreen(onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) })
                }
                composable(Screen.Privacy.route) {
                    PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    @Composable
    private fun SecurityLoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PkyAiBlue)
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt(x * x + y * y + z * z)
        val delta: Float = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta
        if (acceleration > 15 && !voiceViewModel.isRecording.value && mainViewModel.authState.value is AuthState.Authenticated && ::voiceService.isInitialized) {
            Toast.makeText(this, "PKY AI Assistant: Listening...", Toast.LENGTH_SHORT).show()
            voiceViewModel.setRecordingState(true)
            voiceService.startRecording()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        if (::voiceService.isInitialized) {
            voiceService.cleanup()
        }
    }
}
