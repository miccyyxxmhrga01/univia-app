package com.example.kayara

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/** Retains Material behavior and existing view contracts with a shared soft surface. */
class NeoCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {
    private var softSurface: NeumorphicDrawable? = null

    init {
        cardElevation = 0f; maxCardElevation = 0f
        useCompatPadding = false; preventCornerOverlap = false; strokeWidth = 0
        val shape = context.obtainStyledAttributes(attrs,
            intArrayOf(androidx.cardview.R.attr.cardCornerRadius), defStyleAttr, 0)
        val cornerRadius = shape.getDimension(0, resources.getDimension(R.dimen.ui_radius_control))
        shape.recycle()
        radius = cornerRadius
        setCardBackgroundColor(Color.TRANSPARENT)
        softSurface = NeumorphicDrawable(resources, cornerDp = cornerRadius / resources.displayMetrics.density)
        background = softSurface
        clipToOutline = false
    }

    override fun setRadius(radius: Float) {
        super.setRadius(radius)
        softSurface?.setCornerRadius(radius)
        invalidateOutline()
    }
}
