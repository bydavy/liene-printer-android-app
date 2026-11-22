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
        if (savedInstanceState == null ||
            (viewModel.editor.value.source == null && viewModel.collage.value == null)
        ) {
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
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        if (intent.type?.startsWith("image/") != true) return

        val uris = sharedUris(intent)
        when {
            uris.size >= 2 -> viewModel.loadCollage(uris)
            uris.size == 1 -> viewModel.loadImage(uris.first())
        }
    }

    /**
     * Gallery apps normally use EXTRA_STREAM, but some use ClipData (and a few
     * older ones only set the intent data URI).
     */
    private fun sharedUris(intent: Intent): List<Uri> {
        val fromClip = intent.clipData
            ?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri } }
            .orEmpty()
        if (fromClip.isNotEmpty()) return fromClip.distinct()

        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val multiple = IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            if (!multiple.isNullOrEmpty()) return multiple.distinct()
        }

        val single = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_STREAM,
            Uri::class.java,
        ) ?: intent.data
        return listOfNotNull(single)
    }
}
