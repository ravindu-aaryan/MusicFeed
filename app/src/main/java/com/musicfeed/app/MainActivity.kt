package com.musicfeed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.musicfeed.app.instagram.InstagramSession
import com.musicfeed.app.ui.EqualizerScreen
import com.musicfeed.app.ui.LoginScreen
import com.musicfeed.app.ui.MusicFeedTheme
import com.musicfeed.app.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicFeedTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    var loggedIn by rememberSaveable { mutableStateOf(InstagramSession.isLoggedIn()) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }

    when {
        !loggedIn -> LoginScreen(onLoggedIn = { loggedIn = true })
        showEqualizer -> {
            BackHandler { showEqualizer = false }
            EqualizerScreen(onBack = { showEqualizer = false })
        }
        else -> PlayerScreen(
            onOpenEqualizer = { showEqualizer = true },
            onLoggedOut = { loggedIn = false },
        )
    }
}
