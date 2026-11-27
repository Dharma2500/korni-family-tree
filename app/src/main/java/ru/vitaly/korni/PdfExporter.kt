package ru.vitaly.korni

import android.content.Context
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument

object PdfExporter {
    fun exportA4(context: Context, uri: Uri, data: FamilyData) {
        val nodes = TreeLayout.build(data)
        val treeWidth = TreeLayout.width(nodes)
        val treeHeight = TreeLayout.height(nodes)
        val pageWidth = 595
        val pageHeight = 842
        val tileWidth = 535f
        val tileHeight = 750f
        val columns = kotlin.math.ceil(treeWidth / tileWidth).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(treeHeight / tileHeight).toInt().coerceAtLeast(1)
        val totalPages = columns * rows
        val photos: Map<String, Bitmap> = data.people.mapNotNull { person ->
            if (person.photoUri.isBlank()) null
            else PhotoLoader.loadThumbnail(context, person.photoUri)?.let { person.id to it }
        }.toMap()
        val document = PdfDocument()

        try {
            var pageNumber = 1
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    val page = document.startPage(info)
                    val canvas: Canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(64, 77, 54)
                        textSize = 17f
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    }
                    val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(116, 123, 108)
                        textSize = 9f
                    }
                    canvas.drawText("Корни • семейное древо", 24f, 28f, headerPaint)
                    canvas.drawText(
                        "Лист " + pageNumber + " из " + totalPages + " • листы можно соединить по перекрывающимся краям",
                        24f, 44f, notePaint
                    )

                    canvas.save()
                    canvas.translate(24f - column * tileWidth, 58f - row * tileHeight)
                    TreeRenderer.draw(canvas, data, nodes, photos = photos)
                    canvas.restore()

                    document.finishPage(page)
                    pageNumber++
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                document.writeTo(output)
            } ?: error("Не удалось открыть файл для записи")
        } finally {
            document.close()
        }
    }
}
