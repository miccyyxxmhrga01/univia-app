package com.example.kayara

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.content.ContextCompat

/** Shared, stationary selection treatment for the three role dashboards. */
object UniviaNavigation {
    fun update(icons: List<ImageView>, labels: List<TextView>, selectedPosition: Int) {
        icons.forEachIndexed { index, icon ->
            val selected = index == selectedPosition
            val color = ContextCompat.getColor(icon.context,
                if (selected) R.color.brand_navy else R.color.text_secondary)
            icon.animate().cancel()
            icon.translationY = 0f
            icon.scaleX = 1f
            icon.scaleY = 1f
            icon.alpha = 1f
            icon.elevation = 0f
            icon.imageTintList = ColorStateList.valueOf(color)
            icon.clearColorFilter()
            icon.background = NeumorphicDrawable(icon.resources, if (selected) 1 else 0, 16f)
            (icon.parent as? View)?.apply {
                isSelected = selected
                contentDescription = icon.contentDescription ?: labels[index].text
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                ViewCompat.setScreenReaderFocusable(this, true)
                if (background == null) {
                    val mask = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = 16f * resources.displayMetrics.density
                    }
                    background = RippleDrawable(
                        ColorStateList.valueOf((color and 0x00FFFFFF) or 0x18000000), null, mask)
                }
                ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.Button"
                        info.roleDescription = host.context.getString(R.string.ui_navigation_tab)
                        info.isSelected = selected
                        info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                            0, 1, index, 1, false, selected))
                    }
                })
                (parent as? View)?.let { navigation ->
                    ViewCompat.setAccessibilityDelegate(navigation, object : AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.setCollectionInfo(AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                                1, icons.size, false, AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE))
                        }
                    })
                }
            }
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            labels[index].apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                animate().cancel()
                translationY = 0f
                alpha = 1f
                setTextColor(color)
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context,
                    if (selected) R.font.poppins_semibold else R.font.poppins_regular)
            }
        }
    }
}
