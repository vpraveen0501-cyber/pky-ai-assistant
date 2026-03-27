package com.pkyai.android.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebArtifactView(
    htmlContent: String,
    onArtifactEvent: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier.fillMaxSize()
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(type: String, data: String) {
                        onArtifactEvent(type, data)
                    }
                }, "AndroidBridge")
                
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
