package com.deva.ledgercalc.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.deva.ledgercalc.domain.Tape
import java.io.File

/** Writes the tape to a cache file and hands it to the system share sheet. */
object ExportManager {

    fun shareText(context: Context, tape: Tape) {
        val body = TapeText.render(tape)
        if (body.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, "LedgerCalc tape")
        }
        launch(context, Intent.createChooser(intent, "Share tape"))
    }

    fun sharePdf(context: Context, tape: Tape) {
        if (tape.rows.isEmpty()) return
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "ledgercalc-tape.pdf")
        TapePdf.write(context, tape, file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LedgerCalc tape")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, Intent.createChooser(intent, "Share tape"))
    }

    private fun launch(context: Context, chooser: Intent) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
