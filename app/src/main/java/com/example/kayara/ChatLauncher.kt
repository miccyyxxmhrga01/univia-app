package com.example.kayara

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase

/**
 * Abli ang group chat sa usa ka organization.
 */
fun openOrganizationChat(
    context: Context,
    organizationId: String,
    organizationName: String,
    asAdmin: Boolean
) {

    val intent = Intent(context, ChatActivity::class.java)

    intent.putExtra(ChatActivity.EXTRA_ORG_ID, organizationId)
    intent.putExtra(ChatActivity.EXTRA_ORG_NAME, organizationName)
    intent.putExtra(ChatActivity.EXTRA_AS_ADMIN, asAdmin)

    context.startActivity(intent)
}

/**
 * Abli ang group chat sa Super Admin + tanang organization admins.
 */
fun openAdminsGroupChat(
    context: Context,
    asSuperAdmin: Boolean
) {

    val intent = Intent(context, ChatActivity::class.java)

    intent.putExtra(ChatActivity.EXTRA_ORG_ID, ChatActivity.ADMINS_ROOM_ID)
    intent.putExtra(ChatActivity.EXTRA_ORG_NAME, "Org Managers Group Chat")
    intent.putExtra(ChatActivity.EXTRA_AS_ADMIN, true)
    intent.putExtra(ChatActivity.EXTRA_AS_SUPER_ADMIN, asSuperAdmin)

    context.startActivity(intent)
}

// =========================================================
// PICKER DATA + HELPERS
// =========================================================

private data class ChatOrg(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val colorStart: String,
    val colorEnd: String
)

private fun Context.dpPx(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

private fun safeColor(value: String, fallback: String): Int {
    return try {
        Color.parseColor(value)
    } catch (e: Exception) {
        Color.parseColor(fallback)
    }
}

private fun roundedBg(
    color: Int,
    radius: Float,
    topOnly: Boolean = false
): GradientDrawable {

    val drawable = GradientDrawable()
    drawable.setColor(color)

    if (topOnly) {
        drawable.cornerRadii = floatArrayOf(
            radius, radius, radius, radius, 0f, 0f, 0f, 0f
        )
    } else {
        drawable.cornerRadius = radius
    }

    return drawable
}

private fun getChatInitials(name: String): String {

    val words =
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    return when {
        words.isEmpty() -> "ORG"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3)
            .map { it.first() }
            .joinToString("")
            .uppercase()
    }
}

// =========================================================
// PICKER (bottom sheet, parehas sa color scheme sa app)
// =========================================================

private fun showChatPicker(
    activity: Activity,
    orgs: List<ChatOrg>
) {

    val navy = Color.parseColor("#3B176A")
    val gray = Color.parseColor("#6B5C7B")

    val dialog = BottomSheetDialog(activity)

    val root = LinearLayout(activity)
    root.orientation = LinearLayout.VERTICAL
    root.background =
        roundedBg(Color.WHITE, activity.dpPx(24).toFloat(), true)
    root.setPadding(
        activity.dpPx(16), 0, activity.dpPx(16), activity.dpPx(20)
    )

    // Handle
    val handle = View(activity)
    val handleParams = LinearLayout.LayoutParams(
        activity.dpPx(40), activity.dpPx(4)
    )
    handleParams.gravity = Gravity.CENTER_HORIZONTAL
    handleParams.topMargin = activity.dpPx(10)
    handleParams.bottomMargin = activity.dpPx(12)
    handle.layoutParams = handleParams
    handle.background = roundedBg(Color.parseColor("#D6C6E7"), 100f)
    root.addView(handle)

    // Title
    val title = TextView(activity)
    title.text = "Choose a group chat"
    title.setTextColor(navy)
    title.textSize = 17f
    title.setTypeface(null, Typeface.BOLD)
    title.setPadding(activity.dpPx(4), 0, activity.dpPx(4), 0)
    root.addView(title)

    val subtitle = TextView(activity)
    subtitle.text = "Select an organization to open its chat"
    subtitle.setTextColor(gray)
    subtitle.textSize = 12f
    subtitle.setPadding(
        activity.dpPx(4), activity.dpPx(2), activity.dpPx(4), activity.dpPx(12)
    )
    root.addView(subtitle)

    // Rows
    orgs.forEach { org ->

        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background =
            roundedBg(Color.parseColor("#F1ECF8"), activity.dpPx(16).toFloat())
        row.setPadding(
            activity.dpPx(12), activity.dpPx(12),
            activity.dpPx(14), activity.dpPx(12)
        )
        row.isClickable = true
        row.isFocusable = true

        val rowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rowParams.bottomMargin = activity.dpPx(10)
        row.layoutParams = rowParams

        // Avatar (initials + picture)
        val avatarBox = FrameLayout(activity)
        avatarBox.layoutParams =
            LinearLayout.LayoutParams(activity.dpPx(46), activity.dpPx(46))

        val initials = TextView(activity)
        initials.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        initials.gravity = Gravity.CENTER
        initials.text = getChatInitials(org.name)
        initials.setTextColor(Color.WHITE)
        initials.textSize = 12f
        initials.setTypeface(null, Typeface.BOLD)

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                safeColor(org.colorStart, "#7C3AED"),
                safeColor(org.colorEnd, "#7C3AED")
            )
        )
        gradient.cornerRadius = activity.dpPx(14).toFloat()
        initials.background = gradient

        val image = ImageView(activity)
        image.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.visibility = View.GONE

        avatarBox.addView(initials)
        avatarBox.addView(image)

        if (org.avatarUrl.isNotBlank()) {

            image.visibility = View.VISIBLE
            initials.visibility = View.GONE

            Glide.with(activity)
                .load(org.avatarUrl)
                .circleCrop()
                .into(image)
        }

        // Text
        val column = LinearLayout(activity)
        column.orientation = LinearLayout.VERTICAL

        val columnParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
        columnParams.marginStart = activity.dpPx(12)
        column.layoutParams = columnParams

        val name = TextView(activity)
        name.text = org.name
        name.setTextColor(navy)
        name.textSize = 14f
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        name.setTypeface(null, Typeface.BOLD)

        val meta = TextView(activity)
        meta.text = "Group chat"
        meta.setTextColor(gray)
        meta.textSize = 11f

        column.addView(name)
        column.addView(meta)

        // Arrow
        val arrow = TextView(activity)
        arrow.text = "›"
        arrow.setTextColor(Color.parseColor("#7C3AED"))
        arrow.textSize = 24f

        row.addView(avatarBox)
        row.addView(column)
        row.addView(arrow)

        row.setOnClickListener {

            dialog.dismiss()

            openOrganizationChat(
                activity,
                org.id,
                org.name,
                false
            )
        }

        root.addView(row)
    }

    // Cancel
    val cancel = TextView(activity)
    cancel.text = "Cancel"
    cancel.setTextColor(Color.parseColor("#7C3AED"))
    cancel.textSize = 13f
    cancel.setTypeface(null, Typeface.BOLD)
    cancel.gravity = Gravity.CENTER
    cancel.setPadding(0, activity.dpPx(10), 0, activity.dpPx(6))
    cancel.isClickable = true
    cancel.isFocusable = true
    cancel.setOnClickListener { dialog.dismiss() }
    root.addView(cancel)

    dialog.setOnShowListener {

        val sheet =
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener

        sheet.setBackgroundColor(Color.TRANSPARENT)

        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    dialog.setContentView(root)
    dialog.show()
}

/**
 * Para sa student: kung usa ra ang approved organization, direkta na.
 * Kung daghan (max 3), mopili una kung asa nga org chat.
 */
fun openStudentChat(activity: Activity) {

    val uid = FirebaseAuth.getInstance().currentUser?.uid

    if (uid == null) {

        Toast.makeText(
            activity,
            "Please log in again.",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    val db = SupabaseDatabase.getInstance()

    // Kuhaon una ang tinuod nga organizations
    db.collection("organizations")
        .get()
        .addOnSuccessListener { orgSnapshot ->

            if (activity.isFinishing || activity.isDestroyed) {
                return@addOnSuccessListener
            }

            val orgInfo =
                orgSnapshot.documents.associate { document ->
                    document.id to document
                }

            db.collection("memberships")
                .whereEqualTo("studentId", uid)
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener { snapshot ->

                    if (activity.isFinishing || activity.isDestroyed) {
                        return@addOnSuccessListener
                    }

                    val orgs =
                        snapshot.documents
                            .mapNotNull { document ->

                                val orgId =
                                    document.getString("organizationId")
                                        ?.trim()
                                        ?: ""

                                val orgDoc = orgInfo[orgId]

                                // Tinuod nga organization ra
                                if (orgId.isBlank() || orgDoc == null) {
                                    null
                                } else {
                                    ChatOrg(
                                        id = orgId,
                                        name =
                                            orgDoc.getString("orgName")
                                                ?.takeIf { it.isNotBlank() }
                                                ?: document.getString("organizationName")
                                                ?: "Organization",
                                        avatarUrl =
                                            orgDoc.getString("avatarUrl") ?: "",
                                        colorStart =
                                            orgDoc.getString("colorStart")
                                                ?: "#7C3AED",
                                        colorEnd =
                                            orgDoc.getString("colorEnd")
                                                ?: "#7C3AED"
                                    )
                                }
                            }
                            .distinctBy { it.id }
                            .take(3)

                    when {

                        orgs.isEmpty() -> {

                            Toast.makeText(
                                activity,
                                "Join an organization first to use the group chat.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        orgs.size == 1 -> {

                            openOrganizationChat(
                                activity,
                                orgs[0].id,
                                orgs[0].name,
                                false
                            )
                        }

                        else -> {

                            showChatPicker(activity, orgs)
                        }
                    }
                }
                .addOnFailureListener { error ->

                    if (activity.isFinishing || activity.isDestroyed) {
                        return@addOnFailureListener
                    }

                    Toast.makeText(
                        activity,
                        "Unable to load your organizations: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
        .addOnFailureListener { error ->

            if (activity.isFinishing || activity.isDestroyed) {
                return@addOnFailureListener
            }

            Toast.makeText(
                activity,
                "Unable to load organizations: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
}

// =========================================================
// ADMIN CHAT CHOOSER
// Admin: org members chat O admins group chat
// =========================================================

private fun chooserRow(
    activity: Activity,
    titleText: String,
    subText: String,
    onClick: () -> Unit
): LinearLayout {

    val row = LinearLayout(activity)
    row.orientation = LinearLayout.VERTICAL
    row.background =
        roundedBg(Color.parseColor("#F1ECF8"), activity.dpPx(16).toFloat())
    row.setPadding(
        activity.dpPx(16), activity.dpPx(14),
        activity.dpPx(16), activity.dpPx(14)
    )
    row.isClickable = true
    row.isFocusable = true

    val params = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    params.bottomMargin = activity.dpPx(10)
    row.layoutParams = params

    val t = TextView(activity)
    t.text = titleText
    t.setTextColor(Color.parseColor("#3B176A"))
    t.textSize = 14f
    t.maxLines = 1
    t.ellipsize = android.text.TextUtils.TruncateAt.END
    t.setTypeface(null, Typeface.BOLD)

    val s = TextView(activity)
    s.text = subText
    s.setTextColor(Color.parseColor("#6B5C7B"))
    s.textSize = 11f

    row.addView(t)
    row.addView(s)
    row.setOnClickListener { onClick() }

    return row
}

/**
 * Para sa admin: pilia kung org members chat o admins group chat.
 */
fun showAdminChatChooser(
    activity: Activity,
    organizationId: String,
    organizationName: String
) {

    val dialog = BottomSheetDialog(activity)

    val root = LinearLayout(activity)
    root.orientation = LinearLayout.VERTICAL
    root.background =
        roundedBg(Color.WHITE, activity.dpPx(24).toFloat(), true)
    root.setPadding(
        activity.dpPx(16), activity.dpPx(20),
        activity.dpPx(16), activity.dpPx(20)
    )

    val title = TextView(activity)
    title.text = "Choose a group chat"
    title.setTextColor(Color.parseColor("#3B176A"))
    title.textSize = 17f
    title.setTypeface(null, Typeface.BOLD)
    title.setPadding(activity.dpPx(4), 0, 0, activity.dpPx(12))
    root.addView(title)

    root.addView(
        chooserRow(
            activity,
            organizationName.ifBlank { "My Organization" },
            "Group chat · Your members"
        ) {
            dialog.dismiss()
            openOrganizationChat(
                activity,
                organizationId,
                organizationName,
                true
            )
        }
    )

    root.addView(
        chooserRow(
            activity,
            "Org Managers Group Chat",
            "Coordinator + all organization admins"
        ) {
            dialog.dismiss()
            openAdminsGroupChat(activity, false)
        }
    )

    dialog.setOnShowListener {

        val sheet =
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener

        sheet.setBackgroundColor(Color.TRANSPARENT)

        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    dialog.setContentView(root)
    dialog.show()
}