package ru.krugzaboty.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.krugzaboty.app.ui.navigation.KrugNavHost
import ru.krugzaboty.app.ui.theme.KrugTheme
import kotlinx.coroutines.runBlocking
import ru.krugzaboty.app.data.settings.SettingsStore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboarded = runBlocking { settings.onboardingDoneOnce() }
        setContent {
            KrugTheme {
                KrugNavHost(startOnboarded = onboarded)
            }
        }
    }
}
