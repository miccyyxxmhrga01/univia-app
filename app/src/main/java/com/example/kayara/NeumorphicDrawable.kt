package com.example.kayara

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser

/** Directional shadows cached per size/state, never blurred on each animation frame. */
class NeumorphicDrawable() : Drawable() {
    private var density = Resources.getSystem().displayMetrics.density
    private var kind = 0
    private var radius = 22f * density
    private var pressed = false
    private var enabled = true
    private var selected = false
    private var focused = false
    private lateinit var palette: CampusSurfaces.Palette
    private var cached: Bitmap? = null
    private val outputPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    constructor(resources: Resources, style: Int = 0, cornerDp: Float = 22f) : this() {
        density = resources.displayMetrics.density; kind = style; radius = cornerDp * density
        palette = CampusSurfaces.Palette(resources)
    }

    override fun inflate(res: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(res, parser, attrs, theme)
        density = res.displayMetrics.density
        palette = CampusSurfaces.Palette(res, theme)
        val values = res.obtainAttributes(attrs, R.styleable.NeumorphicDrawable)
        kind = values.getInt(R.styleable.NeumorphicDrawable_neoStyle, 0)
        radius = values.getDimension(R.styleable.NeumorphicDrawable_neoRadius, 22f * density)
        values.recycle()
    }

    override fun onBoundsChange(bounds: Rect) { cached = null }
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val nextPressed = android.R.attr.state_pressed in state
        val nextEnabled = android.R.attr.state_enabled in state
        val nextSelected = android.R.attr.state_selected in state
        val nextFocused = android.R.attr.state_focused in state
        if (pressed == nextPressed && enabled == nextEnabled && selected == nextSelected && focused == nextFocused) return false
        pressed = nextPressed; enabled = nextEnabled; selected = nextSelected; focused = nextFocused
        cached = null; invalidateSelf(); return true
    }

    /** Pixel units, matching CardView.setRadius so runtime shape changes stay in sync. */
    fun setCornerRadius(radiusPx: Float) {
        val nextRadius = radiusPx.coerceAtLeast(0f)
        if (radius == nextRadius) return
        radius = nextRadius
        cached = null
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val bitmap = cached ?: render().also { cached = it }
        canvas.drawBitmap(bitmap, null, bounds, outputPaint)
    }

    private fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val inset = ((if (kind == 1) 6f else 7f) * density).coerceAtMost(minOf(bounds.width(), bounds.height()) / 5f)
        val r = RectF(inset, inset, bitmap.width - inset, bitmap.height - inset)
        val corner = minOf(radius, r.height() / 2f)
        val style = if (kind == 0 && selected) 1 else kind
        val shapeRadius = if (style == 2) r.height() / 2f else corner
        CampusSurfaces.draw(c, r, shapeRadius, density, style, pressed, enabled, focused || selected, palette)
        return bitmap
    }

    override fun setAlpha(alpha: Int) { outputPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { outputPaint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
