package com.example.kayara

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class OrgMemberItem(
    val name: String,
    val subtitle: String,
    val isOfficer: Boolean,
    val photoUrl: String,
    val rank: Int,

    // NEW: actual officer position
    val position: String = ""
)

/**
 * Order sa officers:
 * President
 * Vice President
 * Secretary
 * Treasurer
 * Auditor
 * PIO
 * uban pa
 * regular members sa katapusan
 */
fun officerRank(
    position: String,
    isOfficer: Boolean
): Int {

    if (!isOfficer) {
        return 100
    }

    val p =
        position
            .trim()
            .lowercase()

    return when {

        /*
         * IMPORTANT:
         * Check Vice President before President,
         * kay ang "vice president" adunay word nga "president".
         */
        p.contains("vice president") ||
                p.contains("vice") -> 1

        p.contains("president") -> 0

        p.contains("secretary") -> 2

        p.contains("treasurer") -> 3

        p.contains("auditor") -> 4

        p.contains("pio") ||
                p.contains("public information") -> 5

        p.contains("business") -> 6

        p.contains("sergeant") ||
                p.contains("peace officer") -> 7

        else -> 8
    }
}

class OrgMemberAdapter(
    private val items: List<OrgMemberItem>
) : RecyclerView.Adapter<OrgMemberAdapter.Holder>() {

    class Holder(
        view: View,
        val initials: TextView,
        val photo: ImageView,
        val name: TextView,
        val subtitle: TextView,
        val badge: TextView
    ) : RecyclerView.ViewHolder(view)

    // =========================================================
    // DP
    // =========================================================

    private fun Context.memberDp(
        value: Int
    ): Int {

        return (
                value *
                        resources
                            .displayMetrics
                            .density
                ).toInt()
    }

    // =========================================================
    // CREATE VIEW
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): Holder {

        val context =
            parent.context

        // =====================================================
        // CARD
        // =====================================================

        val card =
            LinearLayout(context)

        card.orientation =
            LinearLayout.HORIZONTAL

        card.gravity =
            Gravity.CENTER_VERTICAL

        card.setBackgroundResource(
            R.drawable.bg_card
        )

        card.setPadding(
            context.memberDp(14),
            context.memberDp(14),
            context.memberDp(14),
            context.memberDp(14)
        )

        val cardParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        cardParams.bottomMargin =
            context.memberDp(10)

        card.layoutParams =
            cardParams

        // =====================================================
        // AVATAR
        // =====================================================

        val avatarBox =
            FrameLayout(context)

        avatarBox.layoutParams =
            LinearLayout.LayoutParams(
                context.memberDp(56),
                context.memberDp(56)
            )

        val initials =
            TextView(context)

        initials.layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        initials.gravity =
            Gravity.CENTER

        initials.setTextColor(
            Color.WHITE
        )

        initials.textSize =
            17f

        initials.setTypeface(
            null,
            Typeface.BOLD
        )

        val photo =
            ImageView(context)

        photo.layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        photo.scaleType =
            ImageView.ScaleType.CENTER_CROP

        photo.visibility =
            View.GONE

        avatarBox.addView(
            initials
        )

        avatarBox.addView(
            photo
        )

        // =====================================================
        // NAME + SUBTITLE
        // =====================================================

        val column =
            LinearLayout(context)

        column.orientation =
            LinearLayout.VERTICAL

        val columnParams =
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

        columnParams.marginStart =
            context.memberDp(14)

        columnParams.marginEnd =
            context.memberDp(8)

        column.layoutParams =
            columnParams

        val name =
            TextView(context)

        name.setTextColor(
            Color.parseColor(
                "#3B176A"
            )
        )

        name.textSize =
            16f

        name.setTypeface(
            null,
            Typeface.BOLD
        )

        name.maxLines =
            1

        name.ellipsize =
            TextUtils.TruncateAt.END

        val subtitle =
            TextView(context)

        subtitle.setTextColor(
            Color.parseColor(
                "#6B5C7B"
            )
        )

        subtitle.textSize =
            13f

        subtitle.maxLines =
            2

        subtitle.ellipsize =
            TextUtils.TruncateAt.END

        column.addView(
            name
        )

        column.addView(
            subtitle
        )

        // =====================================================
        // BADGE
        // =====================================================

        val badge =
            TextView(context)

        badge.textSize =
            11f

        badge.setTypeface(
            null,
            Typeface.BOLD
        )

        badge.gravity =
            Gravity.CENTER

        badge.maxLines =
            1

        badge.ellipsize =
            TextUtils.TruncateAt.END

        badge.setPadding(
            context.memberDp(12),
            context.memberDp(6),
            context.memberDp(12),
            context.memberDp(6)
        )

        /*
         * Prevent very long positions from making
         * the whole row ugly.
         */
        badge.maxWidth =
            context.memberDp(145)

        // =====================================================
        // ADD VIEWS
        // =====================================================

        card.addView(
            avatarBox
        )

        card.addView(
            column
        )

        card.addView(
            badge
        )

        return Holder(
            card,
            initials,
            photo,
            name,
            subtitle,
            badge
        )
    }

    // =========================================================
    // BIND
    // =========================================================

    override fun onBindViewHolder(
        holder: Holder,
        position: Int
    ) {

        val item =
            items[position]

        val context =
            holder.itemView.context

        // =====================================================
        // NAME + SUBTITLE
        // =====================================================

        holder.name.text =
            item.name

        holder.subtitle.text =
            item.subtitle

        holder.initials.text =
            initialsOf(
                item.name
            )

        // =====================================================
        // AVATAR BACKGROUND
        // =====================================================

        val circle =
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,

                if (
                    item.isOfficer
                ) {

                    intArrayOf(
                        Color.parseColor(
                            "#7C3AED"
                        ),
                        Color.parseColor(
                            "#7C3AED"
                        )
                    )

                } else {

                    intArrayOf(
                        Color.parseColor(
                            "#3B176A"
                        ),
                        Color.parseColor(
                            "#3B176A"
                        )
                    )
                }
            )

        circle.shape =
            GradientDrawable.OVAL

        holder.initials.background =
            circle

        // =====================================================
        // PROFILE PICTURE
        // =====================================================

        if (
            item.photoUrl.isNotBlank()
        ) {

            holder.photo.visibility =
                View.VISIBLE

            holder.initials.visibility =
                View.GONE

            Glide.with(
                context
            )
                .load(
                    item.photoUrl
                )
                .circleCrop()
                .into(
                    holder.photo
                )

        } else {

            Glide.with(
                context
            )
                .clear(
                    holder.photo
                )

            holder.photo.setImageDrawable(
                null
            )

            holder.photo.visibility =
                View.GONE

            holder.initials.visibility =
                View.VISIBLE
        }

        // =====================================================
        // BADGE
        // =====================================================

        val pill =
            GradientDrawable()

        pill.cornerRadius =
            context
                .memberDp(20)
                .toFloat()

        if (
            item.isOfficer
        ) {

            /*
             * IMPORTANT:
             * Actual position instead of generic OFFICER.
             */
            holder.badge.text =
                item.position
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.uppercase()
                    ?: "OFFICER"

            holder.badge.setTextColor(
                Color.parseColor(
                    "#7C3AED"
                )
            )

            pill.setColor(
                Color.parseColor(
                    "#F1ECF8"
                )
            )

        } else {

            holder.badge.text =
                "MEMBER"

            holder.badge.setTextColor(
                Color.parseColor(
                    "#6B5C7B"
                )
            )

            pill.setColor(
                Color.parseColor(
                    "#E1E8F2"
                )
            )
        }

        holder.badge.background =
            pill
    }

    // =========================================================
    // COUNT
    // =========================================================

    override fun getItemCount():
            Int {

        return items.size
    }

    // =========================================================
    // INITIALS
    // =========================================================

    private fun initialsOf(
        name: String
    ): String {

        val parts =
            name
                .trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        return when {

            parts.size >= 2 -> {

                "${parts[0].first()}${parts[1].first()}"
                    .uppercase()
            }

            parts.size == 1 -> {

                parts[0]
                    .take(2)
                    .uppercase()
            }

            else -> {

                "ST"
            }
        }
    }
}