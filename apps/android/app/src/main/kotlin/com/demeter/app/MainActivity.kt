package com.demeter.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.navigation.compose.rememberNavController
import com.demeter.app.ui.DemeterNavHost
import com.demeter.app.ui.DemeterViewModel
import com.demeter.app.ui.theme.DemeterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DemeterViewModel by viewModels()

    /** Set from notification taps; validated because the extra is attacker-controllable. */
    private val pendingAccountId = mutableStateOf<String?>(null)

    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private fun extractAccountId(intent: Intent?): String? =
        intent?.getStringExtra("accountId")?.takeIf { uuidRegex.matches(it) }

    /** A screenshot shared into Demeter via ACTION_SEND, for on-device OCR import. */
    private val sharedImage = mutableStateOf<Uri?>(null)

    private fun extractSharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAccountId.value = extractAccountId(intent)
        sharedImage.value = extractSharedImage(intent)
        setContent {
            DemeterTheme {
                val navController = rememberNavController()
                DemeterNavHost(navController = navController, viewModel = viewModel)
                // Privacy mode: FLAG_SECURE blocks screenshots/screen recording and
                // redacts the app in the recents switcher. Applied reactively.
                val secure = viewModel.privacySecure.value
                LaunchedEffect(secure) {
                    if (secure) {
                        window.setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SECURE,
                            android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                val target = pendingAccountId.value
                LaunchedEffect(target) {
                    if (target != null && viewModel.onboarded) {
                        pendingAccountId.value = null
                        runCatching { navController.navigate("account/$target") }
                    }
                }
                val shared = sharedImage.value
                LaunchedEffect(shared) {
                    if (shared != null) {
                        sharedImage.value = null
                        viewModel.ingestSharedImage(shared) {
                            runCatching { navController.navigate("import") }
                        }
                    }
                }
            }
        }
    }

    // singleTask: a notification tap while the app is running lands here, not onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingAccountId.value = extractAccountId(intent)
        extractSharedImage(intent)?.let { sharedImage.value = it }
    }
}
