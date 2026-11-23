package com.deva.ledgercalc.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.deva.ledgercalc.domain.Amounts
import com.deva.ledgercalc.domain.Tape
import java.io.File

/**
 * Writes the tape to a PDF using the platform's [PdfDocument], so the feature costs no extra
 * dependency. The layout mirrors the on-screen tape: operator gutter, right-aligned amounts, a
 * rule, then the total.
 *
 * The gutter carries the operator and the amount carries its own sign, as in [TapeText]: a row
 * that subtracts must not read like a row that adds.
 */
object TapePdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val ROW_HEIGHT = 22f

    /** Space kept free at the bottom of every page for the page number. */
    private const val FOOTER_SPACE = 32f

    /** Space the rule and the total need, reserved before the last row is placed on a page. */
    private const val TOTAL_BLOCK = 4f + ROW_HEIGHT + 8f

    fun write(tape: Tape, file: File) {
        val document = PdfDocument()
        val amountPaint = paint(13f, Typeface.MONOSPACE)
        val expressionPaint = paint(11f, Typeface.MONOSPACE)
        val footerPaint = paint(9f, Typeface.MONOSPACE).apply {
            color = 0xFF666666.toInt()
        }
        val titlePaint = paint(
            18f,
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        )
        val rulePaint = Paint().apply {
            strokeWidth = 1f
            isAntiAlias = true
        }

        try {
            val rows = tape.rows
            val amounts = rows.map { Amounts.format(it.amount) }
            val totalText = Amounts.format(tape.total)
            val columnRight = PAGE_WIDTH - MARGIN
            val widest = (amounts + totalText).maxOfOrNull { it.length } ?: 0
            val amountLeft = columnRight - amountPaint.measureText("0".repeat(widest.coerceAtLeast(1)))
            val contentBottom = PAGE_HEIGHT - MARGIN - FOOTER_SPACE
            val rowLimit = contentBottom - TOTAL_BLOCK

            var index = 0
            var pageNumber = 1
            while (index < rows.size || pageNumber == 1) {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                val canvas = page.canvas
                var y = MARGIN + 20f

                val heading = if (pageNumber == 1) "LedgerCalc" else "LedgerCalc (continued)"
                canvas.drawText(heading, MARGIN, y, titlePaint)
                y += 28f

                while (index < rows.size && y <= rowLimit) {
                    val row = rows[index]
                    canvas.drawText(row.operator?.symbol ?: "", MARGIN, y, amountPaint)
                    val amount = amounts[index]
                    canvas.drawText(amount, columnRight - amountPaint.measureText(amount), y, amountPaint)

                    val expression = row.expression
                    if (expression != null) {
                        // Never let a long grouping run into the amount column.
                        val room = amountLeft - 8f - (MARGIN + 24f)
                        if (room > 0f && expressionPaint.measureText(expression) <= room) {
                            canvas.drawText(expression, MARGIN + 24f, y, expressionPaint)
                        }
                    }

                    y += ROW_HEIGHT
                    index++
                }

                if (index >= rows.size) {
                    y += 4f
                    canvas.drawLine(amountLeft, y, columnRight, y, rulePaint)
                    y += ROW_HEIGHT
                    canvas.drawText("=", MARGIN, y, amountPaint)
                    canvas.drawText(
                        totalText,
                        columnRight - amountPaint.measureText(totalText),
                        y,
                        amountPaint
                    )
                }

                // A multi-page export is only re-checkable if the pages can be told apart.
                val footer = "Page $pageNumber"
                canvas.drawText(
                    footer,
                    columnRight - footerPaint.measureText(footer),
                    PAGE_HEIGHT - 28f,
                    footerPaint
                )

                document.finishPage(page)
                pageNumber++
            }

            file.outputStream().use { document.writeTo(it) }
        } finally {
            // Without this the native document leaks whenever writing the file fails.
            document.close()
        }
    }

    private fun paint(size: Float, typeface: Typeface): Paint = Paint().apply {
        textSize = size
        isAntiAlias = true
        this.typeface = typeface
    }
}
