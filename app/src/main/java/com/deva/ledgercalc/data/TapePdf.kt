package com.deva.ledgercalc.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.deva.ledgercalc.domain.Amounts
import com.deva.ledgercalc.domain.Tape
import java.io.File

/**
 * Writes the tape to a PDF using the platform's [PdfDocument], so the feature costs no extra
 * dependency. The layout mirrors the on-screen tape: operator gutter, right-aligned amounts,
 * a rule, then the total.
 */
object TapePdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val ROW_HEIGHT = 22f

    fun write(context: Context, tape: Tape, file: File) {
        val document = PdfDocument()
        val amountPaint = Paint().apply {
            textSize = 13f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val titlePaint = Paint().apply {
            textSize = 18f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val rulePaint = Paint().apply {
            strokeWidth = 1f
            isAntiAlias = true
        }

        val rows = tape.rows
        val amounts = rows.map { Amounts.format(it.amount) }
        val totalText = Amounts.format(tape.total)
        val columnRight = PAGE_WIDTH - MARGIN
        val amountWidth = amountPaint.measureText(
            (amounts + totalText).maxOfOrNull { it.length }?.let { "0".repeat(it) } ?: "0"
        )
        val amountLeft = columnRight - amountWidth

        var index = 0
        var pageNumber = 1
        while (index < rows.size || pageNumber == 1) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            val canvas = page.canvas
            var y = MARGIN + 20f

            if (pageNumber == 1) {
                canvas.drawText("LedgerCalc", MARGIN, y, titlePaint)
                y += 28f
            }

            while (index < rows.size && y < PAGE_HEIGHT - MARGIN - ROW_HEIGHT) {
                val row = rows[index]
                val sign = if (index == 0) {
                    if (row.amount.signum() < 0) "-" else " "
                } else {
                    row.operator?.symbol ?: "+"
                }
                val body = amounts[index].removePrefix("-")
                canvas.drawText(sign, MARGIN, y, amountPaint)
                canvas.drawText(body, columnRight - amountPaint.measureText(body), y, amountPaint)
                if (row.expression != null) {
                    canvas.drawText(row.expression.orEmpty(), MARGIN + 24f, y, amountPaint)
                }
                y += ROW_HEIGHT
                index++
            }

            if (index >= rows.size) {
                y += 4f
                canvas.drawLine(amountLeft, y, columnRight, y, rulePaint)
                y += ROW_HEIGHT
                val totalBody = totalText.removePrefix("-")
                val sign = if (totalText.startsWith("-")) "-" else "="
                canvas.drawText(sign, MARGIN, y, amountPaint)
                canvas.drawText(totalBody, columnRight - amountPaint.measureText(totalBody), y, amountPaint)
            }

            document.finishPage(page)
            pageNumber++
        }

        file.outputStream().use { document.writeTo(it) }
        document.close()
    }
}
