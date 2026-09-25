package com.example.mikayala

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mikayala.data.repository.MikayalaRepository
import com.example.mikayala.theme.DeepNight
import com.example.mikayala.theme.MikayalaTheme
import com.example.mikayala.ui.screens.*
import kotlinx.coroutines.launch

enum class AppNavigationState {
    SPLASH,
    AUTH,
    ONBOARDING,
    SET_FIRST_PASSWORD,
    LOCK,
    DECOY_CALCULATOR,
    MAIN_APP,
    ERROR
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: MikayalaRepository
    private var startupError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MikayalaStartup", "MainActivity.onCreate started")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d("MikayalaStartup", "Initializing repository...")
        try {
            repository = MikayalaRepository(applicationContext)
            Log.d("MikayalaStartup", "Repository initialized successfully")
            
            Log.d("MikayalaStartup", "Creating notification channels...")
            com.example.mikayala.util.NotificationHelper.createNotificationChannels(applicationContext)
            Log.d("MikayalaStartup", "Notification channels created")

            // Demande de permission pour les notifications sous Android 13+ (API 33+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            }
        } catch (e: Throwable) {
            Log.e("MikayalaStartup", "CRITICAL ERROR during initialization: ${e.message}", e)
            startupError = "${e.javaClass.simpleName}: ${e.message}"
        }

        Log.d("MikayalaStartup", "Setting content view...")
        setContent {
            val userSettings = if (::repository.isInitialized) {
                repository.userSettings.collectAsState().value
            } else {
                com.example.mikayala.data.model.UserSettingsEntity() // Default fallback
            }

            MikayalaTheme(darkTheme = userSettings.isDarkMode) {
                var navigationState by remember { 
                    mutableStateOf(if (startupError != null) AppNavigationState.ERROR else AppNavigationState.SPLASH) 
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (userSettings.isDarkMode) DeepNight else Color.White
                ) {
                    Crossfade(targetState = navigationState, label = "main_navigation_crossfade") { state ->
                        when (state) {
                            AppNavigationState.ERROR -> {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(24.dp).background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Désolé, Mikayala n'a pas pu démarrer ⚠️",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            startupError ?: "Erreur d'initialisation inconnue",
                                            color = Color.LightGray,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text(
                                            "Veuillez vérifier votre connexion ou les clés API.",
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            AppNavigationState.SPLASH -> {
                                SplashScreen(
                                    onSplashFinished = {
                                        lifecycleScope.launch {
                                            if (repository.getCurrentUserId().isEmpty()) {
                                                navigationState = AppNavigationState.AUTH
                                            } else {
                                                repository.findExistingCoupleSpace()
                                                val coupleSpace = repository.activeCoupleSpace.value
                                                val userSettings = repository.userSettings.value
                                                if (coupleSpace.isPaired) {
                                                    if (userSettings.hasLocalPassword && userSettings.pinCode.isNotEmpty()) {
                                                        navigationState = AppNavigationState.LOCK
                                                    } else if (!userSettings.hasCompletedPairingSetup || userSettings.pinCode.isEmpty()) {
                                                        navigationState = AppNavigationState.SET_FIRST_PASSWORD
                                                    } else {
                                                        navigationState = AppNavigationState.MAIN_APP
                                                    }
                                                } else {
                                                    navigationState = AppNavigationState.ONBOARDING
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            AppNavigationState.AUTH -> {
                                AuthScreen(
                                    repository = repository,
                                    onAuthSuccess = {
                                        lifecycleScope.launch {
                                            repository.findExistingCoupleSpace()
                                            val coupleSpace = repository.activeCoupleSpace.value
                                            val userSettings = repository.userSettings.value
                                            if (coupleSpace.isPaired) {
                                                if (userSettings.hasLocalPassword && userSettings.pinCode.isNotEmpty()) {
                                                    navigationState = AppNavigationState.LOCK
                                                } else if (!userSettings.hasCompletedPairingSetup) {
                                                    navigationState = AppNavigationState.SET_FIRST_PASSWORD
                                                } else {
                                                    navigationState = AppNavigationState.MAIN_APP
                                                }
                                            } else {
                                                navigationState = AppNavigationState.ONBOARDING
                                            }
                                        }
                                    }
                                )
                            }
                            AppNavigationState.SET_FIRST_PASSWORD -> {
                                SetFirstPasswordScreen(
                                    repository = repository,
                                    onPasswordSet = {
                                        val couple = repository.activeCoupleSpace.value
                                        if (couple.isPaired) {
                                            navigationState = AppNavigationState.MAIN_APP
                                        } else {
                                            navigationState = AppNavigationState.ONBOARDING
                                        }
                                    },
                                    onSkipPassword = {
                                        val couple = repository.activeCoupleSpace.value
                                        if (couple.isPaired) {
                                            navigationState = AppNavigationState.MAIN_APP
                                        } else {
                                            navigationState = AppNavigationState.ONBOARDING
                                        }
                                    }
                                )
                            }
                            AppNavigationState.LOCK -> {
                                SecurityLockScreen(
                                    repository = repository,
                                    onUnlockSuccess = {
                                        lifecycleScope.launch {
                                            repository.findExistingCoupleSpace()
                                            val coupleSpace = repository.activeCoupleSpace.value
                                            if (repository.getCurrentUserId().isEmpty()) {
                                                navigationState = AppNavigationState.AUTH
                                            } else if (!coupleSpace.isPaired) {
                                                navigationState = AppNavigationState.ONBOARDING
                                            } else {
                                                navigationState = AppNavigationState.MAIN_APP
                                            }
                                        }
                                    },
                                    onDecoyTriggered = {
                                        navigationState = AppNavigationState.DECOY_CALCULATOR
                                    }
                                )
                            }
                            AppNavigationState.DECOY_CALCULATOR -> {
                                DecoyCalculatorScreen(
                                    onExitDecoy = {
                                        navigationState = AppNavigationState.LOCK
                                    }
                                )
                            }
                            AppNavigationState.MAIN_APP -> {
                                val coupleSpace = repository.activeCoupleSpace.value
                                if (!coupleSpace.isPaired) {
                                    LaunchedEffect(Unit) {
                                        navigationState = AppNavigationState.ONBOARDING
                                    }
                                } else {
                                    MainDashboardScreen(
                                        repository = repository,
                                        onLockApp = {
                                            val userSettings = repository.userSettings.value
                                            if (userSettings.hasLocalPassword && userSettings.pinCode.isNotEmpty()) {
                                                navigationState = AppNavigationState.LOCK
                                            } else {
                                                android.widget.Toast.makeText(this@MainActivity, "Aucun code PIN configuré", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                            AppNavigationState.ONBOARDING -> {
                                OnboardingScreen(
                                    repository = repository,
                                    onCompleteOnboarding = {
                                        lifecycleScope.launch {
                                            repository.findExistingCoupleSpace()
                                            val coupleSpace = repository.activeCoupleSpace.value
                                            val userSettings = repository.userSettings.value
                                            if (coupleSpace.isPaired) {
                                                if (userSettings.hasLocalPassword && userSettings.pinCode.isNotEmpty()) {
                                                    navigationState = AppNavigationState.LOCK
                                                } else if (!userSettings.hasCompletedPairingSetup) {
                                                    navigationState = AppNavigationState.SET_FIRST_PASSWORD
                                                } else {
                                                    navigationState = AppNavigationState.MAIN_APP
                                                }
                                            } else {
                                                navigationState = AppNavigationState.ONBOARDING
                                            }
                                        }
                                    },
                                    onBackToApp = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
