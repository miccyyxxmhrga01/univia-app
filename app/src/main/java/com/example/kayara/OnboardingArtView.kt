package com.example.kayara

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Decorative native artwork; the accessible page copy carries all product information. */
class OnboardingArtView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var page: Int = 0
        set(value) { field = value; cached = null; invalidate() }
    private var cached: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = ContextCompat.getColor(context, R.color.brand_violet)
    private val accent = ContextCompat.getColor(context, R.color.brand_orange)
    private val surfacePalette = CampusSurfaces.Palette(resources, context.theme)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { cached = null }
    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val bitmap = cached ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            val scale = minOf(width / 320f, height / 260f)
            c.translate((width - 320f * scale) / 2, (height - 260f * scale) / 2)
            c.scale(scale, scale)
            when (page) { 0 -> discover(c); 1 -> membership(c); else -> community(c) }
            cached = it
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }
    private fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float = 22f) {
        CampusSurfaces.draw(c, RectF(x, y, x + w, y + h), r, 1f, 0, false, true, palette = surfacePalette)
    }
    private fun icon(c: Canvas, id: Int, x: Float, y: Float, size: Int, tint: Boolean = true) {
        ContextCompat.getDrawable(context, id)?.mutate()?.apply {
            if (tint) setTint(ink)
            setBounds(x.toInt(), y.toInt(), x.toInt() + size, y.toInt() + size)
            draw(c)
        }
    }
    private fun line(c: Canvas, x: Float, y: Float, w: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(196, 174, 220)
        c.drawRoundRect(x, y, x + w, y + 7f, 3.5f, 3.5f, paint)
    }
    private fun discover(c: Canvas) {
        paint.color = Color.rgb(175, 146, 205); paint.strokeWidth = 1.5f
        c.drawLine(80f, 62f, 240f, 197f, paint); c.drawLine(240f, 62f, 80f, 197f, paint)
        tile(c, 99f, 69f, 122f, 122f, 61f)
        paint.style = Paint.Style.STROKE; paint.color = accent; paint.strokeWidth = 3f
        c.drawArc(RectF(94f, 64f, 226f, 196f), -50f, 66f, false, paint)
        paint.style = Paint.Style.FILL
        icon(c, R.drawable.univia_logo, 112f, 82f, 96, false)
        for ((x, y, id) in listOf(Triple(26f, 21f, R.drawable.ic_grad_cap), Triple(236f, 21f, R.drawable.ic_interest), Triple(26f, 180f, R.drawable.ic_nav_members), Triple(236f, 180f, R.drawable.ic_building))) {
            tile(c, x, y, 58f, 58f, 20f); icon(c, id, x + 15, y + 15, 28)
        }
    }
    private fun membership(c: Canvas) {
        tile(c, 58f, 24f, 198f, 210f)
        tile(c, 78f, 44f, 48f, 48f, 16f); icon(c, R.drawable.ic_folder, 90f, 56f, 24)
        line(c, 144f, 53f, 82f); line(c, 144f, 72f, 56f)
        for (y in listOf(117f, 151f, 185f)) {
            icon(c, R.drawable.ic_check_circle_filled, 82f, y - 4, 22)
            line(c, 122f, y + 2, 103f)
        }
        tile(c, 233f, 166f, 61f, 61f, 30f); icon(c, R.drawable.ic_verified, 248f, 181f, 30)
    }
    private fun community(c: Canvas) {
        tile(c, 34f, 38f, 190f, 166f)
        icon(c, R.drawable.ic_calendar, 52f, 55f, 29)
        line(c, 98f, 63f, 99f)
        for (row in 0..2) for (col in 0..3) {
            paint.color = if (row == 1 && col == 2) accent else Color.rgb(196, 174, 220)
            c.drawRoundRect(57f + col * 35, 111f + row * 25, 72f + col * 35, 126f + row * 25, 5f, 5f, paint)
        }
        tile(c, 181f, 134f, 111f, 94f)
        icon(c, R.drawable.ic_chat_message, 211f, 158f, 47)
        tile(c, 244f, 24f, 49f, 49f, 24f); icon(c, R.drawable.ic_bell, 257f, 37f, 24)
    }
}
