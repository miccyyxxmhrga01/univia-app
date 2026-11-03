package com.example.kayara

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser

/** Shared glass/soft-depth paint; all blurs are cached, never recomputed during motion. */
object CampusSurfaces {
    /** Resolve once for each drawable/artwork cache, rather than looking up colors while drawing. */
    class Palette(private val resources: Resources, private val theme: Resources.Theme? = null) {
        private fun color(id: Int) = ResourcesCompat.getColor(resources, id, theme)
        val primary = intArrayOf(color(R.color.action_blue_dark), color(R.color.action_blue), color(R.color.action_blue_end))
        val primaryPressed = intArrayOf(color(R.color.action_pressed_start), color(R.color.action_pressed_end))
        val primaryDisabled = intArrayOf(color(R.color.action_disabled_start), color(R.color.action_disabled_end))
        val raised = intArrayOf(color(R.color.surface_raised_start), color(R.color.surface_raised_end))
        val inset = intArrayOf(color(R.color.surface_inset_start), color(R.color.surface_inset_end))
        val disabled = intArrayOf(color(R.color.surface_disabled_start), color(R.color.surface_disabled_end))
        val edge = intArrayOf(color(R.color.surface_edge_start), color(R.color.surface_edge_middle), color(R.color.surface_edge_end))
        val shadow = color(R.color.surface_shadow)
        val actionShadow = color(R.color.surface_action_shadow)
        val focus = color(R.color.control_focus)
    }

    fun draw(canvas: Canvas, rect: RectF, radius: Float, density: Float, style: Int, pressed: Boolean, enabled: Boolean, focused: Boolean = false, palette: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val primary = style == 2
        val colors = when {
            primary && !enabled -> palette.primaryDisabled
            primary && pressed -> palette.primaryPressed
            primary -> palette.primary
            !enabled -> palette.disabled
            style == 1 || pressed -> palette.inset
            else -> palette.raised
        }
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, if (primary) rect.top else rect.bottom, colors, null, Shader.TileMode.CLAMP)
        if (style != 1 && enabled && !pressed) {
            // A single upper-left light source: subtle depth at rest, a little more for actions.
            paint.setShadowLayer((if (primary) 5f else 4f) * density,
                1.5f * density, (if (primary) 4f else 3f) * density,
                if (primary) palette.actionShadow else palette.shadow)
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.clearShadowLayer()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (style == 1) 1.5f * density else density
        paint.shader = LinearGradient(0f, rect.top, rect.right, rect.bottom,
            palette.edge, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (focused && enabled) {
            paint.shader = null
            paint.color = palette.focus
            paint.strokeWidth = 1.5f * density
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}

open class CampusGlassCard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val scenePosition = IntArray(2)
    private val cardPosition = IntArray(2)
    private val glassClip = Path()
    private val glassBounds = RectF()
    private val density = resources.displayMetrics.density
    private var sceneView: android.view.View? = null
    private val scrollListener = android.view.ViewTreeObserver.OnScrollChangedListener { invalidate() }

    init {
        background = CampusGlassDrawable(resources)
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sceneView = findScene()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
    }

    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        sceneView = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = 5f * density
        glassBounds.set(inset, inset, w - inset, h - inset)
        glassClip.reset()
        glassClip.addRoundRect(glassBounds, 32f * density, 32f * density, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        val scene = sceneView ?: findScene().also { sceneView = it }
        val backdrop = scene?.background as? CampusBackdropDrawable
        if (backdrop != null && width > 0 && height > 0) {
            // Sample the same scene at the card's current position, including while scrolling.
            // Only this background is blurred; labels and native input controls stay sharp.
            scene.getLocationInWindow(scenePosition)
            getLocationInWindow(cardPosition)
            val saved = canvas.save()
            canvas.clipPath(glassClip)
            canvas.translate((scenePosition[0] - cardPosition[0]).toFloat(), (scenePosition[1] - cardPosition[1]).toFloat())
            backdrop.drawFrosted(canvas)
            canvas.restoreToCount(saved)
        }
        super.draw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipPath(glassClip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }

    private fun findScene(): android.view.View? {
        var candidate = parent as? android.view.View
        while (candidate != null) {
            if (candidate.background is CampusBackdropDrawable) return candidate
            candidate = candidate.parent as? android.view.View
        }
        return null
    }
}

/** Kept for the existing login layouts; onboarding shares the same glass material. */
class CampusLoginCard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : CampusGlassCard(context, attrs)

private class CampusGlassDrawable(resources: Resources) : Drawable() {
    private val density = resources.displayMetrics.density
    private var cached: Bitmap? = null
    private val output = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onBoundsChange(bounds: Rect) { cached = null }
    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val bitmap = cached ?: Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            val inset = 5f * density
            val r = RectF(inset, inset, it.width - inset, it.height - inset)
            val radius = 32f * density
            val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(r.left, r.top, r.right, r.bottom,
                    intArrayOf(0x66FBF6FF, 0x52F2E8FC, 0x70E9DCF7),
                    floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
                setShadowLayer(12f * density, 0f, 7f * density, 0x40634D80)
            }
            c.drawRoundRect(r, radius, radius, glass)
            glass.clearShadowLayer()
            c.save()
            c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
            // A diffused white reflection keeps text clear while the edges remain translucent.
            glass.shader = RadialGradient(r.centerX(), r.top + 90f * density, r.width() * .75f,
                intArrayOf(0x22FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(r, glass)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun wave(color: Int, path: Path) { p.color = color; c.drawPath(path, p) }
            wave(0x669763E5, Path().apply {
                moveTo(r.left, r.top); lineTo(r.left + 100 * density, r.top)
                cubicTo(r.left + 60*density, r.top + 32*density, r.left + 20*density, r.top + 12*density, r.left, r.top + 68*density); close()
            })
            wave(0x446D28D9, Path().apply {
                moveTo(r.left, r.top); lineTo(r.left + 62*density, r.top)
                cubicTo(r.left + 24*density, r.top + 22*density, r.left + 18*density, r.top + 55*density, r.left, r.top + 66*density); close()
            })
            wave(0x76966BE6, Path().apply {
                moveTo(r.right - 145*density, r.bottom); cubicTo(r.right - 96*density, r.bottom - 28*density, r.right - 40*density, r.bottom - 14*density, r.right, r.bottom - 68*density)
                lineTo(r.right, r.bottom); close()
            })
            wave(0xFFFF943D.toInt(), Path().apply {
                moveTo(r.right - 100*density, r.bottom); quadTo(r.right - 22*density, r.bottom - 24*density, r.right, r.bottom - 44*density)
                lineTo(r.right, r.bottom); close()
            })
            wave(0xF06D28D9.toInt(), Path().apply {
                moveTo(r.right - 122*density, r.bottom); cubicTo(r.right - 82*density, r.bottom - 24*density, r.right - 26*density, r.bottom - 18*density, r.right, r.bottom - 28*density)
                lineTo(r.right, r.bottom); close()
            })
            c.restore()
            p.color = Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = density
            c.drawRoundRect(r, radius, radius, p)
            cached = it
        }
        canvas.drawBitmap(bitmap, null, bounds, output)
    }
    override fun setAlpha(alpha: Int) { output.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { output.colorFilter = filter; invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** The supplied campus photo fills the scene, with a wider crop above the glass form. */
class CampusBackdropDrawable : Drawable() {
    private var photo: Bitmap? = null
    private var frostedPhoto: Bitmap? = null
    private var cached: Bitmap? = null
    private var cachedFrosted: Bitmap? = null
    private var navigationInsetBottom = 0
    private val output = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun inflate(res: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(res, parser, attrs, theme)
        photo = CampusPhoto.get(res)
        frostedPhoto = CampusPhoto.get(res, frosted = true)
    }
    override fun onBoundsChange(bounds: Rect) { cached = null; cachedFrosted = null }
    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        updateViewportInsets()
        val bitmap = cached ?: renderScene(bounds.width(), bounds.height(), photo).also { cached = it }
        canvas.drawBitmap(bitmap, null, bounds, output)
    }

    fun drawFrosted(canvas: Canvas) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        updateViewportInsets()
        // Half-resolution cache is sufficient for the frosted layer and uses less memory.
        val bitmap = cachedFrosted ?: renderScene(maxOf(1, bounds.width() / 2), maxOf(1, bounds.height() / 2), frostedPhoto)
            .also { cachedFrosted = it }
        canvas.drawBitmap(bitmap, null, bounds, output)
    }

    private fun updateViewportInsets() {
        val owner = callback as? android.view.View ?: return
        val bottom = androidx.core.view.ViewCompat.getRootWindowInsets(owner)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        if (bottom != navigationInsetBottom) {
            navigationInsetBottom = bottom
            cached = null
            cachedFrosted = null
        }
    }

    private fun renderScene(width: Int, height: Int, source: Bitmap?): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            c.drawColor(Color.rgb(237, 225, 248))
            val scaledNavigationInset = navigationInsetBottom * it.height.toFloat() / bounds.height()
            val photoBottom = (it.height - scaledNavigationInset).coerceAtLeast(1f)
            val requestedZoom = if ((callback as? android.view.View)?.id == R.id.loginScene) .76f else 1f
            source?.let { image ->
                val sourceHeight = (image.height * .88f).toInt()
                val sourceRect = Rect(0, 0, image.width, sourceHeight)
                // Keep photographic detail beneath the entire panel, rather than fading to
                // a flat blue field that makes even translucent glass look opaque.
                val fullCoverScale = maxOf(it.width.toFloat() / image.width, it.height.toFloat() / sourceHeight)
                val minimumCoverScale = maxOf(it.width.toFloat() / image.width, photoBottom / sourceHeight)
                val coverScale = maxOf(minimumCoverScale, fullCoverScale * requestedZoom)
                val coverWidth = image.width * coverScale
                val coverHeight = sourceHeight * coverScale
                val coverLeft = -(coverWidth - it.width) * .62f
                val coverTop = -maxOf(0f, coverHeight - it.height) * .25f
                c.drawBitmap(image, sourceRect,
                    RectF(coverLeft, coverTop, coverLeft + coverWidth, coverTop + coverHeight), p)

                // A wider upper crop keeps the photographed seal visible above the form.
                // Blend into the full-height photo; there is no opaque fade beneath the card.
                val originalHeight = minOf(it.width * .88f, it.height * .4f).coerceAtLeast(it.width * .5f)
                val originalWidth = image.width * originalHeight / sourceHeight
                val originalLeft = -(originalWidth - it.width) * .34f
                val zoom = maxOf(requestedZoom, it.width / originalWidth)
                // Zoom around the campus seal so widening the photo does not move it under the card.
                val imageHeight = originalHeight * zoom
                val scaledWidth = originalWidth * zoom
                val left = originalLeft + (it.width * .3f - originalLeft) * (1f - zoom)
                val top = originalHeight * .68f * (1f - zoom)
                val bottom = top + imageHeight
                val saved = c.saveLayer(0f, 0f, it.width.toFloat(), bottom, null)
                c.drawBitmap(image, sourceRect, RectF(left, top, left + scaledWidth, bottom), p)
                p.shader = if (top > 0f) {
                    val topBlendEnd = top + minOf(it.width * .04f, imageHeight * .1f)
                    LinearGradient(0f, 0f, 0f, bottom,
                        intArrayOf(0x00FFFFFF, 0x00FFFFFF, Color.WHITE, Color.WHITE, 0x00FFFFFF),
                        floatArrayOf(0f, top / bottom, topBlendEnd / bottom, (top + imageHeight * .78f) / bottom, 1f),
                        Shader.TileMode.CLAMP)
                } else {
                    LinearGradient(0f, imageHeight * .78f, 0f, bottom,
                        Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                c.drawPaint(p)
                p.xfermode = null
                p.shader = null
                c.restoreToCount(saved)
            }
            // No full-screen tint: the upper 80% remains photographic.
            // Reach white before an opaque navigation bar covers the lower edge.
            val fadeStart = it.height * .8f
            val fadeEnd = maxOf(fadeStart + 1f, it.height - scaledNavigationInset)
            p.shader = LinearGradient(0f, fadeStart, 0f, fadeEnd,
                0x00FFFFFF, Color.WHITE, Shader.TileMode.CLAMP)
            c.drawRect(0f, fadeStart, it.width.toFloat(), it.height.toFloat(), p)
        }
    override fun setAlpha(alpha: Int) { output.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { output.colorFilter = filter; invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.OPAQUE
}

private object CampusPhoto {
    private var cached: Bitmap? = null
    private var cachedFrosted: Bitmap? = null
    @Synchronized fun get(resources: Resources, frosted: Boolean = false): Bitmap {
        (if (frosted) cachedFrosted else cached)?.let { return it }
        val original = BitmapFactory.decodeResource(resources, R.drawable.login_bg,
            BitmapFactory.Options().apply { inScaled = false })
        val w = original.width; val h = original.height
        var pixels = IntArray(w * h)
        original.getPixels(pixels, 0, w, 0, 0, w, h)
        original.recycle()
        // A separable box blur on the small source avoids per-frame GPU blur on older phones.
        repeat(if (frosted) 3 else 2) {
            val horizontal = IntArray(w * h); val vertical = IntArray(w * h); val radius = if (frosted) 5 else 1; val count = radius * 2 + 1
            for (y in 0 until h) {
                var red = 0; var green = 0; var blue = 0
                for (dx in -radius..radius) { val v = pixels[y*w + dx.coerceIn(0,w-1)]; red += Color.red(v); green += Color.green(v); blue += Color.blue(v) }
                for (x in 0 until w) {
                    horizontal[y*w+x] = Color.rgb(red/count,green/count,blue/count)
                    val before = pixels[y*w+(x-radius).coerceIn(0,w-1)]; val after = pixels[y*w+(x+radius+1).coerceIn(0,w-1)]
                    red += Color.red(after)-Color.red(before); green += Color.green(after)-Color.green(before); blue += Color.blue(after)-Color.blue(before)
                }
            }
            for (x in 0 until w) {
                var red = 0; var green = 0; var blue = 0
                for (dy in -radius..radius) { val v = horizontal[dy.coerceIn(0,h-1)*w+x]; red += Color.red(v); green += Color.green(v); blue += Color.blue(v) }
                for (y in 0 until h) {
                    vertical[y*w+x] = Color.rgb(red/count,green/count,blue/count)
                    val before = horizontal[(y-radius).coerceIn(0,h-1)*w+x]; val after = horizontal[(y+radius+1).coerceIn(0,h-1)*w+x]
                    red += Color.red(after)-Color.red(before); green += Color.green(after)-Color.green(before); blue += Color.blue(after)-Color.blue(before)
                }
            }
            pixels = vertical
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888).also {
            if (frosted) cachedFrosted = it else cached = it
        }
    }
}
