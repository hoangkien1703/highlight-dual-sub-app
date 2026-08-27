package com.kienhoang.highlightdualsub

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var originalText: TextView
    private lateinit var translatedText: TextView
    private lateinit var statusText: TextView

    private val tokenRegex = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*""")
    private lateinit var translator: Translator
    private var translationReady = false
    private var translationGeneration = 0

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        configureTranslator()

        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(HighlightBridge(), "HighlightBridge")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                statusText.text = "YouTube loaded • enabling native caption observer"
                injectNativeCaptionObserver()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl("https://m.youtube.com/")
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        statusText = TextView(this).apply {
            text = "Highlight Dual Sub Lab • open an English YouTube video"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 0, 0, 0))
            textSize = 12f
            setPadding(18, 10, 18, 10)
        }
        root.addView(
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        val subtitlePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(225, 0, 0, 0))
            setPadding(24, 18, 24, 20)
            isClickable = false
            isFocusable = false
        }

        originalText = TextView(this).apply {
            text = "YouTube native caption events will appear here."
            setTextColor(Color.WHITE)
            textSize = 19f
            gravity = Gravity.CENTER
        }
        translatedText = TextView(this).apply {
            text = "Vietnamese translation model is preparing…"
            setTextColor(Color.rgb(180, 220, 255))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        subtitlePanel.addView(originalText)
        subtitlePanel.addView(translatedText)

        root.addView(
            subtitlePanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        setContentView(root)
    }

    private fun configureTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()
        translator = Translation.getClient(options)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translationReady = true
                translatedText.text = "Vietnamese translation ready."
            }
            .addOnFailureListener {
                translatedText.text = "Translation model unavailable; highlighting still works."
            }
    }

    private fun translateCaption(text: String) {
        if (!translationReady) return
        val generation = ++translationGeneration
        translator.translate(text)
            .addOnSuccessListener { translated ->
                if (generation == translationGeneration) translatedText.text = translated
            }
            .addOnFailureListener {
                if (generation == translationGeneration) translatedText.text = "Translation unavailable"
            }
    }

    private fun renderCaption(text: String, activeWordIndex: Int) {
        val matches = tokenRegex.findAll(text).toList()
        val rendered = SpannableString(text)
        val match = matches.getOrNull(activeWordIndex)
        if (match != null) {
            rendered.setSpan(
                BackgroundColorSpan(Color.rgb(255, 235, 59)),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            rendered.setSpan(
                ForegroundColorSpan(Color.BLACK),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            rendered.setSpan(
                UnderlineSpan(),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        originalText.text = rendered
    }

    private fun chooseActiveWord(text: String, previousText: String?): Int {
        val current = tokenRegex.findAll(text.lowercase()).map { it.value }.toList()
        if (current.isEmpty()) return -1
        val previous = previousText
            ?.let { tokenRegex.findAll(it.lowercase()).map { match -> match.value }.toList() }
            .orEmpty()

        if (previous.isNotEmpty() && current.size > previous.size && current.take(previous.size) == previous) {
            return current.lastIndex
        }

        var commonPrefix = 0
        while (
            commonPrefix < current.size &&
            commonPrefix < previous.size &&
            current[commonPrefix] == previous[commonPrefix]
        ) {
            commonPrefix += 1
        }
        return if (commonPrefix < current.size) commonPrefix else current.lastIndex
    }

    private fun injectNativeCaptionObserver() {
        webView.evaluateJavascript(NATIVE_CAPTION_OBSERVER_SCRIPT, null)
    }

    private inner class HighlightBridge {
        @JavascriptInterface
        fun onEvent(raw: String) {
            val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
            if (event.optString("type") != "caption") return
            val text = event.optString("text").trim()
            if (text.isBlank()) return
            val previous = event.optString("previousText").takeIf { it.isNotBlank() }
            val second = event.optDouble("currentSecond", Double.NaN)
            val activeWord = chooseActiveWord(text, previous)

            runOnUiThread {
                renderCaption(text, activeWord)
                translateCaption(text)
                val tokenCount = tokenRegex.findAll(text).count()
                statusText.text = if (second.isFinite()) {
                    "YouTube DOM event @ %.3fs • word %d/%d".format(
                        second,
                        (activeWord + 1).coerceAtLeast(0),
                        tokenCount,
                    )
                } else {
                    "YouTube DOM caption event • word ${(activeWord + 1).coerceAtLeast(0)}/$tokenCount"
                }
            }
        }
    }

    override fun onDestroy() {
        translator.close()
        webView.removeJavascriptInterface("HighlightBridge")
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private val NATIVE_CAPTION_OBSERVER_SCRIPT =
            """
            (function() {
              const host = location.hostname.toLowerCase().replace(/\.$/, '');
              if (location.protocol !== 'https:' ||
                  !(host === 'youtube.com' || host.endsWith('.youtube.com'))) return;
              if (window.__highlightDualSubLabInstalled) return;
              window.__highlightDualSubLabInstalled = true;

              const bridge = window.HighlightBridge;
              if (!bridge || typeof bridge.onEvent !== 'function') return;

              let captionRoot = null;
              let captionObserver = null;
              let lastCaptionText = '';
              let lastMediaTime = null;
              let captionEnableAttempts = 0;

              function activeVideo() {
                const videos = Array.from(document.querySelectorAll('video'));
                return videos.find(v => !v.paused && !v.ended) ||
                  videos.find(v => v.readyState > 0) || videos[0] || null;
              }

              function post(payload) {
                try { bridge.onEvent(JSON.stringify(payload)); } catch (_) {}
              }

              function frameLoop() {
                const video = activeVideo();
                if (!video) {
                  setTimeout(frameLoop, 120);
                  return;
                }
                if (typeof video.requestVideoFrameCallback === 'function') {
                  video.requestVideoFrameCallback(function(_, metadata) {
                    lastMediaTime = metadata && Number.isFinite(metadata.mediaTime)
                      ? metadata.mediaTime
                      : video.currentTime;
                    frameLoop();
                  });
                } else {
                  lastMediaTime = video.currentTime;
                  setTimeout(frameLoop, 40);
                }
              }

              function ensureCaptionsEnabled() {
                if (captionEnableAttempts > 20) return;
                const button = document.querySelector('.ytp-subtitles-button');
                if (button && button.getAttribute('aria-pressed') === 'false') {
                  captionEnableAttempts += 1;
                  try { button.click(); } catch (_) {}
                }
              }

              function hideYouTubeCaptionPaintOnly() {
                if (document.getElementById('highlight-dual-sub-hide-native-caption')) return;
                const style = document.createElement('style');
                style.id = 'highlight-dual-sub-hide-native-caption';
                style.textContent =
                  '.ytp-caption-window-container,.caption-window{' +
                  'opacity:0.001!important;pointer-events:none!important;}';
                (document.head || document.documentElement).appendChild(style);
              }

              function visibleCaptionText() {
                if (!captionRoot) return '';
                return Array.from(captionRoot.querySelectorAll('.ytp-caption-segment'))
                  .map(node => node.textContent || '')
                  .join(' ')
                  .replace(/\s+/g, ' ')
                  .trim();
              }

              function emitCaption() {
                const text = visibleCaptionText();
                if (!text || text === lastCaptionText) return;
                const previousText = lastCaptionText;
                lastCaptionText = text;
                const video = activeVideo();
                const currentSecond = Number.isFinite(lastMediaTime)
                  ? lastMediaTime
                  : (video && Number.isFinite(video.currentTime) ? video.currentTime : null);
                post({
                  type: 'caption',
                  text: text,
                  previousText: previousText || null,
                  currentSecond: currentSecond,
                  url: location.href
                });
              }

              function bindCaptionObserver() {
                const nextRoot = document.querySelector(
                  '.ytp-caption-window-container, .caption-window'
                );
                if (nextRoot === captionRoot) return;
                if (captionObserver) captionObserver.disconnect();
                captionRoot = nextRoot;
                lastCaptionText = '';
                if (!captionRoot) return;
                captionObserver = new MutationObserver(emitCaption);
                captionObserver.observe(captionRoot, {
                  childList: true,
                  subtree: true,
                  characterData: true
                });
                emitCaption();
              }

              frameLoop();
              ensureCaptionsEnabled();
              hideYouTubeCaptionPaintOnly();
              bindCaptionObserver();
              setInterval(function() {
                ensureCaptionsEnabled();
                hideYouTubeCaptionPaintOnly();
                bindCaptionObserver();
              }, 300);
            })();
            """.trimIndent()
    }
}
