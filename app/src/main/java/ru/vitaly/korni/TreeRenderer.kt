package ru.vitaly.korni

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.toColorInt

object TreeRenderer {
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#8B927F".toColorInt()
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val siblingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#A5A994".toColorInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(7f, 6f), 0f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#7C866E".toColorInt()
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        data: FamilyData,
        nodes: List<TreeNode>,
        selectedId: String? = null,
        photos: Map<String, Bitmap> = emptyMap()
    ) {
        val byId = nodes.associateBy { it.person.id }

        data.links.forEach { link ->
            val first = byId[link.fromId] ?: return@forEach
            val second = byId[link.toId] ?: return@forEach
            when (link.type) {
                LinkType.PARENT -> {
                    val startX = first.x + TreeLayout.CARD_WIDTH / 2f
                    val startY = first.y + TreeLayout.CARD_HEIGHT
                    val endX = second.x + TreeLayout.CARD_WIDTH / 2f
                    val endY = second.y - 5f
                    val middleY = (startY + endY) / 2f
                    val path = Path().apply {
                        moveTo(startX, startY)
                        lineTo(startX, middleY)
                        lineTo(endX, middleY)
                        lineTo(endX, endY - 5f)
                    }
                    canvas.drawPath(path, connectorPaint)
                    val arrow = Path().apply {
                        moveTo(endX, endY)
                        lineTo(endX - 5f, endY - 8f)
                        lineTo(endX + 5f, endY - 8f)
                        close()
                    }
                    canvas.drawPath(arrow, arrowPaint)
                }
                LinkType.SPOUSE -> {
                    val left = if (first.x <= second.x) first else second
                    val right = if (first.x <= second.x) second else first
                    val y = left.y + TreeLayout.CARD_HEIGHT / 2f
                    canvas.drawLine(
                        left.x + TreeLayout.CARD_WIDTH,
                        y,
                        right.x,
                        right.y + TreeLayout.CARD_HEIGHT / 2f,
                        connectorPaint
                    )
                    canvas.drawCircle((left.x + TreeLayout.CARD_WIDTH + right.x) / 2f, y, 3.5f, arrowPaint)
                }
                LinkType.SIBLING -> {
                    val left = if (first.x <= second.x) first else second
                    val right = if (first.x <= second.x) second else first
                    val y = left.y + TreeLayout.CARD_HEIGHT / 2f
                    canvas.drawLine(
                        left.x + TreeLayout.CARD_WIDTH,
                        y,
                        right.x,
                        right.y + TreeLayout.CARD_HEIGHT / 2f,
                        siblingPaint
                    )
                }
            }
        }

        nodes.forEach { node ->
            val person = node.person
            val rect = RectF(
                node.x,
                node.y,
                node.x + TreeLayout.CARD_WIDTH,
                node.y + TreeLayout.CARD_HEIGHT
            )
            val fill = when (person.gender) {
                Gender.MALE -> "#EEF3F5".toColorInt()
                Gender.FEMALE -> "#F7EEF0".toColorInt()
                Gender.UNKNOWN -> "#F0F2E9".toColorInt()
            }
            val accent = when (person.gender) {
                Gender.MALE -> "#66889A".toColorInt()
                Gender.FEMALE -> "#B47B8D".toColorInt()
                Gender.UNKNOWN -> "#87936E".toColorInt()
            }
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fill
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (person.id == selectedId) "#526A43".toColorInt() else "#D8DCCF".toColorInt()
                style = Paint.Style.STROKE
                strokeWidth = if (person.id == selectedId) 3f else 1.2f
            }
            canvas.drawRoundRect(rect, 13f, 13f, cardPaint)
            canvas.drawRoundRect(rect, 13f, 13f, borderPaint)

            val photoRect = RectF(node.x + 13f, node.y + 26f, node.x + 57f, node.y + 70f)
            val photo = photos[person.id]
            if (photo != null) {
                val clip = Path().apply {
                    addCircle(node.x + 35f, node.y + 48f, 22f, Path.Direction.CW)
                }
                val save = canvas.save()
                canvas.clipPath(clip)
                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(photo, null, photoRect, imagePaint)
                canvas.restoreToCount(save)
            } else {
                val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawCircle(node.x + 35f, node.y + 48f, 22f, avatarPaint)
                val initial = person.firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                drawText(canvas, initial, node.x + 28f, node.y + 54f, 17f, "#FFFFFF".toColorInt(), true)
            }

            val textLeft = node.x + 65f
            val name = person.displayName().ifBlank { "Без имени" }
            drawText(canvas, name.take(16), textLeft, node.y + 30f, 12.5f, "#30372D".toColorInt(), true)
            drawText(canvas, person.lastName.take(17), textLeft, node.y + 51f, 10.5f, "#596153".toColorInt(), false)
            val dates = when {
                person.birthDate.isBlank() && person.deathDate.isBlank() -> "Даты не указаны"
                person.deathDate.isBlank() -> "Род. " + person.birthDate
                person.birthDate.isBlank() -> "Умер(ла) " + person.deathDate
                else -> person.birthDate + " — " + person.deathDate
            }
            drawText(canvas, dates.take(18), textLeft, node.y + 73f, 8.5f, "#72796A".toColorInt(), false)
        }
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD)
            else Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(value, x, y, paint)
    }
}
