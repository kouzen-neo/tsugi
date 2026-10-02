package com.kouzenneo.tsugi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kouzenneo.tsugi.ui.EditorScreen
import com.kouzenneo.tsugi.ui.EditorViewModel
import com.kouzenneo.tsugi.ui.theme.TsugiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = sharedImages(intent)

        setContent {
            TsugiTheme {
                val vm: EditorViewModel = viewModel()
                EditorScreen(vm, onStart = { if (shared.isNotEmpty()) vm.addUris(shared) })
            }
        }
    }

    /** Accepts "share to Tsugi" from a gallery or screenshot app. */
    private fun sharedImages(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
        )
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
}
