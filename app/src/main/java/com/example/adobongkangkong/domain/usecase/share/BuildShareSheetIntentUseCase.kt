// =====================================================
// 2) Share sheet: build an ACTION_SEND intent
//    (optionally attach banner image)
// =====================================================

package com.example.adobongkangkong.domain.usecase.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject

class BuildShareSheetIntentUseCase @Inject constructor() {

    data class Input(
        val chooserTitle: String = "Share",
        val text: String,
        val attachmentUri: Uri? = null // can be image OR .ics
    )

    operator fun invoke(input: Input): Intent {
        val base = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, input.text)

            if (input.attachmentUri != null) {
                putExtra(Intent.EXTRA_STREAM, input.attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "*/*"
            } else {
                type = "text/plain"
            }
        }

        return Intent.createChooser(base, input.chooserTitle)
    }
}

/**
 * Minimal “least moving parts” helper:
 * take a File in cacheDir and make it shareable via FileProvider.
 *
 * Requires manifest provider setup:
 * <provider
 *   android:name="androidx.core.content.FileProvider"
 *   android:authorities="${applicationId}.fileprovider"
 *   android:exported="false"
 *   android:grantUriPermissions="true">
 *   <meta-data
 *     android:name="android.support.FILE_PROVIDER_PATHS"
 *     android:resource="@xml/file_paths" />
 * </provider>
 *
 * And file_paths.xml allows cache-path.
 */
class FileToShareUriUseCase @Inject constructor(
    private val context: Context
) {
    operator fun invoke(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}

// =====================================================
// Banner image attachment note (no extra moving parts):
// - If you already have a content:// Uri for the banner image, pass it as attachmentUri
//   to BuildShareSheetIntentUseCase.
// - If you only have a File or ByteArray, write it to cacheDir and convert to Uri via FileToShareUriUseCase.
// - Use MIME type image/* if you want to be stricter (optional).
// =====================================================