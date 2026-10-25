package com.musicfeed.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.musicfeed.app.BuildConfig
import com.musicfeed.app.instagram.InstagramSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGIN_URL = "${InstagramSession.BASE_URL}/accounts/login/?next=%2Freels%2F"

/** Shows the real instagram.com login page; we only read the resulting session cookie. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val currentOnLoggedIn by rememberUpdatedState(onLoggedIn)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    fun finishIfLoggedIn(): Boolean {
        if (!InstagramSession.isLoggedIn()) return false
        CookieManager.getInstance().flush()
        currentOnLoggedIn()
        return true
    }

    // Instagram is a single-page app, so page-load callbacks aren't reliable after login.
    LaunchedEffect(Unit) {
        while (!finishIfLoggedIn()) delay(1000)
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Connect Instagram")
                        Text(
                            "Log in so MusicFeed can play your reels feed",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        loadError = null
                        webView?.loadUrl(LOGIN_URL)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload login page")
                    }
                    TextButton(onClick = {
                        if (!finishIfLoggedIn()) {
                            scope.launch {
                                snackbar.showSnackbar("Not logged in yet. Finish logging in on the page first.")
                            }
                        }
                    }) { Text("Continue") }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = InstagramSession.userAgent(context)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url
                                return when (url.scheme) {
                                    "http", "https" -> false
                                    // "Open in app" redirects: follow the web fallback if there is one,
                                    // otherwise stay on the current page instead of a dead error screen.
                                    "intent" -> {
                                        runCatching {
                                            Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
                                                .getStringExtra("browser_fallback_url")
                                        }.getOrNull()?.let(view::loadUrl)
                                        true
                                    }
                                    else -> true
                                }
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loadError = null
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                canGoBack = view.canGoBack()
                                finishIfLoggedIn()
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                                finishIfLoggedIn()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) loadError = error.description?.toString()
                            }
                        }
                        loadUrl(LOGIN_URL)
                        webView = this
                    }
                },
            )

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            loadError?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Couldn't open the Instagram login page",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "$message\n\nCheck your internet connection and try again.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        loadError = null
                        webView?.loadUrl(LOGIN_URL)
                    }) { Text("Retry") }
                }
            }
        }
    }
}
