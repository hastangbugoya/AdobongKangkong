package com.example.adobongkangkong

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.transfer.ImportRecipeBundleUseCase
import com.example.adobongkangkong.domain.transfer.ParseRecipeBundleUseCase
import com.example.adobongkangkong.ui.theme.AdobongKangkongTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesRepository
    @Inject
    lateinit var parseRecipeBundleUseCase: ParseRecipeBundleUseCase
    @Inject
    lateinit var importRecipeBundleUseCase: ImportRecipeBundleUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferences.lockPortrait.collect { locked ->
                    requestedOrientation =
                        if (locked) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                }
            }
        }

        // 🔽 Handle cold start intent
        handleIncomingIntent(intent)

        setContent {
            AdobongKangkongTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // 🔽 Handle when app is already running
        handleIncomingIntent(intent)
    }

    /**
     * Handles incoming intents for recipe import.
     *
     * Supported:
     * - ACTION_VIEW
     * - content:// URIs
     *
     * This stage:
     * - extracts URI
     * - reads file safely
     * - passes raw JSON string to next stage (not yet implemented)
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data: Uri? = intent.data

        if (action == Intent.ACTION_VIEW && data != null) {
            Log.d("AK_IMPORT", "Received ACTION_VIEW with URI: $data")

            lifecycleScope.launch {
                val content = readTextFromUriSafe(data)

                if (content == null) {
                    Log.e("AK_IMPORT", "Failed to read file from URI")
                    return@launch
                }

                Log.d("AK_IMPORT", "File read success, size=${content.length}")

                // 🔜 NEXT STAGE:
                // Pass this JSON string into parser + import pipeline
                // e.g. importRecipeFromJson(content)

                // TEMP placeholder
                val parseResult = parseRecipeBundleUseCase.execute(content)

                when (parseResult) {
                    is ParseRecipeBundleUseCase.Result.Success -> {
                        val importResult = importRecipeBundleUseCase.execute(parseResult.bundle)
                        // show toast
                    }
                    is ParseRecipeBundleUseCase.Result.Failure -> {
                        // show error toast
                    }
                }
            }
        }
    }

    /**
     * Safely reads text from content URI.
     * Never throws — returns null on failure.
     */
    private suspend fun readTextFromUriSafe(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        buildString {
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                append(line).append("\n")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AK_IMPORT", "Error reading URI: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Temporary hook until parser + import use case is implemented.
     */
    private fun onRecipeFileLoaded(json: String) {
        Log.d("AK_IMPORT", "Raw JSON received (preview): ${json.take(200)}")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AdobongKangkongTheme {
        Greeting("Android")
    }
}