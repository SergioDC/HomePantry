package com.homepantry.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

/** Imagen de la semana del menú y su envío con el menú de compartir de Android. */
object WeekShare {
    private const val WIDTH = 1080
    private const val MARGIN = 56
    private const val LABEL_WIDTH = 240

    private val BACKGROUND = 0xFFFFFFFF.toInt()
    private val INK = 0xFF232532.toInt()
    private val SOFT = 0xFF6E6F80.toInt()
    private val BRAND = 0xFF6C5CE7.toInt()
    private val LINE = 0xFFE3E3EA.toInt()
    // Menta oscura (MintPrimary): la clara de la app casi no se lee sobre este fondo blanco.
    private val MINT = 0xFF0F6E56.toInt()

    /**
     * Platos de una franja separados por comas; cada persona con su nombre en menta una sola vez
     * («Pepe: Lentejas, Fruta») y en su propia línea si hay más de un grupo.
     */
    private fun slotText(entries: List<MealEntry>): CharSequence {
        if (entries.isEmpty()) return "—"
        val text = SpannableStringBuilder()
        groupByPerson(entries).forEachIndexed { index, group ->
            if (index > 0) text.append('\n')
            group.person?.let { person ->
                val start = text.length
                text.append("$person:")
                text.setSpan(ForegroundColorSpan(MINT), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.append(' ')
            }
            text.append(group.entries.joinToString(", ") { it.name })
        }
        return text
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Dibuja la semana que empieza en [weekStart]; [entries] puede traer entradas de otras semanas. */
    fun render(weekStart: LocalDate, entries: List<MealEntry>): Bitmap {
        val byDate = entries.groupBy { it.date }
        val brandPaint = textPaint(40f, BRAND, bold = true)
        val titlePaint = textPaint(68f, INK, bold = true)
        val dayPaint = textPaint(46f, BRAND, bold = true)
        val labelPaint = textPaint(34f, SOFT)
        val valuePaint = textPaint(38f, INK)
        val linePaint = Paint().apply {
            color = LINE
            strokeWidth = 3f
        }
        val contentWidth = WIDTH - 2 * MARGIN

        // Mismo código para medir (sin lienzo) y para dibujar: devuelve el alto total.
        fun draw(canvas: Canvas?): Int {
            var y = MARGIN

            fun block(text: CharSequence, paint: TextPaint, x: Int, width: Int): Int {
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
                if (canvas != null) {
                    canvas.save()
                    canvas.translate(x.toFloat(), y.toFloat())
                    layout.draw(canvas)
                    canvas.restore()
                }
                return layout.height
            }

            y += block("SNHome · Menú semanal", brandPaint, MARGIN, contentWidth) + 8
            y += block("${weekRangeLabel(weekStart)} ${weekStart.plusDays(6).year}", titlePaint, MARGIN, contentWidth) + 36
            weekDays(weekStart).forEach { day ->
                y += block(dayLabel(day), dayPaint, MARGIN, contentWidth) + 10
                val dayEntries = byDate[day.toString()].orEmpty()
                MealSlot.values().forEach { slot ->
                    val names = slotText(entriesFor(dayEntries, day.toString(), slot))
                    val labelHeight = block(slot.label, labelPaint, MARGIN, LABEL_WIDTH)
                    val valueHeight = block(
                        names,
                        valuePaint,
                        MARGIN + LABEL_WIDTH,
                        contentWidth - LABEL_WIDTH
                    )
                    y += maxOf(labelHeight, valueHeight) + 10
                }
                y += 14
                canvas?.drawLine(MARGIN.toFloat(), y.toFloat(), (WIDTH - MARGIN).toFloat(), y.toFloat(), linePaint)
                y += 34
            }
            return y - 34 + MARGIN
        }

        val bitmap = Bitmap.createBitmap(WIDTH, draw(null), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        draw(canvas)
        return bitmap
    }

    /** Guarda la imagen como PNG en la caché y abre el menú de compartir de Android. */
    fun share(context: Context, bitmap: Bitmap, chooserTitle: String) {
        val dir = File(context.cacheDir, "menu_share").apply { mkdirs() }
        val file = File(dir, "menu-semanal.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
