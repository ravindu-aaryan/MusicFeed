package com.musicfeed.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.musicfeed.app.instagram.InstagramSession
import kotlinx.coroutines.delay

/** Shows the real instagram.com login page; we only read the resulting session cookie. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val currentOnLoggedIn by rememberUpdatedState(onLoggedIn)

    // Instagram is a single-page app, so page-load callbacks aren't reliable after login.
    LaunchedEffect(Unit) {
        while (!InstagramSession.isLoggedIn()) delay(1000)
        CookieManager.getInstance().flush()
        currentOnLoggedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Connect Instagram")
                    Text(
                        "Log in so MusicFeed can play your reels feed",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            })
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .padding(padding)
                .padding(top = 4.dp)
                .fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl("${InstagramSession.BASE_URL}/accounts/login/")
                }
            },
        )
    }
}
