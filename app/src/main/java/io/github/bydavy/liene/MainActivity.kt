package io.github.bydavy.liene

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import io.github.bydavy.liene.ui.LieneTheme
import io.github.bydavy.liene.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val viewModel: LieneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A configuration change recreates the activity. Do not reprocess the
        // original SEND intent then, or the editor would reload the photo and
        // discard rotation/crop/zoom. After process death the ViewModel is
        // empty, so the intent is handled again.
        if (savedInstanceState == null || viewModel.editor.value.source == null) {
            handleShareIntent(intent)
        }
        setContent {
            LieneTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return
        // Gallery apps normally put the image in EXTRA_STREAM, but some use
        // ClipData (and a few older ones only set the intent data URI).
        val uri = intent.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
            ?: IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            ?: intent.data
            ?: return
        viewModel.loadImage(uri)
    }
}
