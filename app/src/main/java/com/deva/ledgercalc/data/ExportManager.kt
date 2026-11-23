package com.deva.ledgercalc.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.deva.ledgercalc.domain.Tape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the tape to a cache file and hands it to the system share sheet. */
object ExportManager {

    /** How many exported PDFs to keep in the cache before the oldest are swept. */
    private const val KEEP_EXPORTS = 5

    fun shareText(context: Context, tape: Tape): Boolean {
        val body = TapeText.render(tape)
        if (body.isBlank()) return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, "LedgerCalc tape")
        }
        return launch(context, Intent.createChooser(intent, "Share tape"))
    }

    /**
     * Writes the PDF off the main thread, then opens the share sheet.
     *
     * Laying out and writing a multi-page document on the UI thread is what made exporting a long
     * tape stutter, and a fixed filename meant a second export could rewrite the bytes another app
     * was still reading.
     *
     * @return false when the file could not be written or no share sheet could be opened.
     */
    suspend fun sharePdf(context: Context, tape: Tape): Boolean {
        if (tape.rows.isEmpty()) return false
        val appContext = context.applicationContext

        val uri = withContext(Dispatchers.IO) {
            try {
                val directory = File(appContext.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
                prune(directory)
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
                val file = File(directory, "ledgercalc-tape-$stamp.pdf")
                TapePdf.write(tape, file)
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            } catch (_: Exception) {
                null
            }
        } ?: return false

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LedgerCalc tape")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(context, Intent.createChooser(intent, "Share tape"))
    }

    /**
     * Drops the oldest exports. The previous version deleted every file in the directory, which
     * included the PDF a share sheet had just been handed and not yet read.
     */
    private fun prune(directory: File) {
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP_EXPORTS).forEach { it.delete() }
    }

    private fun launch(context: Context, chooser: Intent): Boolean {
        // Any context can land here, so the flag is required rather than incidental.
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    private const val EXPORT_DIRECTORY = "exports"
}
