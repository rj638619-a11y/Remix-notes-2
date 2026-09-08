package com.example.ui.editor

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.GlassTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreviewView(
    htmlContent: String,
    fontSize: Int = 16,
    isJsEnabled: Boolean = true,
    isZenMode: Boolean = false,
    reloadTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors
    val isDark = colors.isDark

    // Prepare complete, well-formed HTML with responsive viewport and adaptive dark/light styling
    val processedHtml = remember(htmlContent, isDark, fontSize) {
        prepareHtmlDocument(
            rawHtml = htmlContent,
            isDark = isDark,
            fontSize = fontSize
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (!isZenMode) {
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                } else Modifier
            )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(if (isDark) 0xFF121214.toInt() else 0xFFFFFFFF.toInt())

                    settings.apply {
                        javaScriptEnabled = isJsEnabled
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        textZoom = ((fontSize / 16f) * 100).toInt().coerceIn(60, 200)
                        defaultTextEncodingName = "utf-8"
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowFileAccess = true
                        allowContentAccess = true
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    return true
                                } catch (_: Throwable) {
                                    // Fallback to in-webview load
                                }
                            }
                            return false
                        }
                    }

                    tag = processedHtml
                    loadDataWithBaseURL("https://local.app/", processedHtml, "text/html", "utf-8", null)
                }
            },
            update = { webView ->
                webView.settings.javaScriptEnabled = isJsEnabled
                webView.settings.textZoom = ((fontSize / 16f) * 100).toInt().coerceIn(60, 200)
                webView.setBackgroundColor(if (isDark) 0xFF121214.toInt() else 0xFFFFFFFF.toInt())
                
                val lastLoaded = webView.tag as? String
                if (lastLoaded != processedHtml) {
                    webView.tag = processedHtml
                    webView.loadDataWithBaseURL("https://local.app/", processedHtml, "text/html", "utf-8", null)
                }
            }
        )
    }
}

/**
 * Ensures the HTML has proper viewport meta tags, charset, and default styling
 * so that snippets or raw markup render cleanly without raw tags or random artifacts.
 */
private fun prepareHtmlDocument(
    rawHtml: String,
    isDark: Boolean,
    fontSize: Int
): String {
    val trimmed = rawHtml.trim()
    if (trimmed.isEmpty()) {
        val emptyBg = if (isDark) "#121214" else "#ffffff"
        val emptyTx = if (isDark) "#6b7280" else "#9ca3af"
        return """<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body {
      background-color: $emptyBg;
      color: $emptyTx;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      height: 80vh;
      font-size: 16px;
      margin: 0;
      padding: 20px;
      text-align: center;
    }
  </style>
</head>
<body>
  <div><i>No HTML content yet. Switch to Code mode to write or paste HTML markup.</i></div>
</body>
</html>"""
    }

    val hasHtmlTag = trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<!doctype", ignoreCase = true)
    val hasHeadTag = trimmed.contains("<head", ignoreCase = true)

    val textColor = if (isDark) "#f1efe8" else "#1d1b16"
    val bgColor = if (isDark) "#121214" else "#ffffff"
    val cardBg = if (isDark) "rgba(255,255,255,0.06)" else "rgba(0,0,0,0.03)"
    val borderColor = if (isDark) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.1)"
    val linkColor = if (isDark) "#60a5fa" else "#2563eb"
    val codeBg = if (isDark) "rgba(255,255,255,0.08)" else "#f3f4f6"

    val baseCss = """
<style id="glass-base-styles">
  :root {
    color-scheme: ${if (isDark) "dark" else "light"};
  }
  html, body {
    margin: 0;
    padding: 16px;
    background-color: $bgColor;
    color: $textColor;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    font-size: ${fontSize}px;
    line-height: 1.6;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
  img, video, canvas, svg {
    max-width: 100%;
    height: auto;
    border-radius: 8px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    font-size: 0.95em;
  }
  th, td {
    padding: 10px 12px;
    border: 1px solid $borderColor;
    text-align: left;
  }
  th {
    background-color: $cardBg;
    font-weight: bold;
  }
  pre {
    background: $codeBg;
    padding: 12px 14px;
    border-radius: 8px;
    overflow-x: auto;
    font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
    font-size: 0.9em;
    border: 1px solid $borderColor;
  }
  code {
    font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
    background: $codeBg;
    padding: 2px 5px;
    border-radius: 4px;
    font-size: 0.9em;
  }
  blockquote {
    margin: 14px 0;
    padding: 4px 0 4px 16px;
    border-left: 3.5px solid $linkColor;
    color: ${if (isDark) "#9ca3af" else "#4b5563"};
    font-style: italic;
  }
  a {
    color: $linkColor;
    text-decoration: underline;
  }
  hr {
    border: none;
    height: 1px;
    background: $borderColor;
    margin: 20px 0;
  }
</style>
""".trimIndent()

    val viewportMeta = """<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">"""

    if (hasHtmlTag) {
        var enriched = trimmed
        if (hasHeadTag) {
            // Inject viewport meta and base fallback CSS inside <head>
            if (!enriched.contains("name=\"viewport\"", ignoreCase = true)) {
                enriched = enriched.replaceFirst(Regex("<head[^>]*>", RegexOption.IGNORE_CASE), "$0\n$viewportMeta\n$baseCss")
            } else {
                enriched = enriched.replaceFirst(Regex("<head[^>]*>", RegexOption.IGNORE_CASE), "$0\n$baseCss")
            }
        } else {
            // No head tag, inject head with viewport
            enriched = enriched.replaceFirst(
                Regex("<html[^>]*>", RegexOption.IGNORE_CASE),
                "$0\n<head>\n$viewportMeta\n$baseCss\n</head>"
            )
        }
        return enriched
    } else {
        // Plain HTML fragment/snippet: Wrap in complete HTML5 structure
        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  $viewportMeta
  $baseCss
</head>
<body>
$trimmed
</body>
</html>"""
    }
}
