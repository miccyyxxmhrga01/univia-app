package com.example.kayara

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object PostInteractions {

    private const val NAVY = "#3B176A"
    private const val GRAY = "#6B5C7B"
    private const val BLUE = "#7C3AED"
    private const val RED = "#AC3C4E"

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
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

    private fun prepareSheet(dialog: BottomSheetDialog) {

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

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    private fun handle(context: Context): View {

        val bar = View(context)

        val params = LinearLayout.LayoutParams(
            context.dp(40),
            context.dp(4)
        )

        params.gravity = Gravity.CENTER_HORIZONTAL
        params.topMargin = context.dp(10)
        params.bottomMargin = context.dp(10)

        bar.layoutParams = params
        bar.background = rounded(Color.parseColor("#D6C6E7"), 100f)

        return bar
    }

    // =========================================================
    // POST MENU (tulo ka dot)
    // =========================================================

    fun showPostMenu(
        context: Context,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {

        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.background = rounded(Color.WHITE, context.dp(24).toFloat(), true)
        root.setPadding(context.dp(16), 0, context.dp(16), context.dp(20))

        root.addView(handle(context))

        fun row(
            iconRes: Int,
            label: String,
            color: Int,
            tintBg: Int,
            action: () -> Unit
        ) {

            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(context.dp(10), context.dp(12), context.dp(10), context.dp(12))
            row.isClickable = true
            row.isFocusable = true

            val iconBox = LinearLayout(context)
            iconBox.gravity = Gravity.CENTER
            iconBox.background = rounded(tintBg, context.dp(12).toFloat())
            iconBox.layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(40))

            val icon = ImageView(context)
            icon.setImageResource(iconRes)
            icon.setColorFilter(color)
            icon.layoutParams = LinearLayout.LayoutParams(context.dp(20), context.dp(20))
            iconBox.addView(icon)

            val text = TextView(context)
            text.text = label
            text.setTextColor(color)
            text.textSize = 14f
            text.setTypeface(null, Typeface.BOLD)

            val textParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            textParams.marginStart = context.dp(14)
            text.layoutParams = textParams

            row.addView(iconBox)
            row.addView(text)

            row.setOnClickListener {
                dialog.dismiss()
                action()
            }

            root.addView(row)
        }

        row(
            android.R.drawable.ic_menu_edit,
            "Edit post",
            Color.parseColor(BLUE),
            Color.parseColor("#F1ECF8"),
            onEdit
        )

        row(
            android.R.drawable.ic_menu_delete,
            "Delete post",
            Color.parseColor(RED),
            Color.parseColor("#F4E1E5"),
            onDelete
        )

        prepareSheet(dialog)
        dialog.setContentView(root)
        dialog.show()
    }

    // =========================================================
    // EDIT POST
    // =========================================================

    fun showEditDialog(
        context: Context,
        post: FeedPostModel
    ) {

        val isPhoto = post.postType == "photo"

        val input = EditText(context)
        input.setText(if (isPhoto) post.caption else post.textContent)
        input.setSelection(input.text.length)
        input.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.minLines = 3
        input.gravity = Gravity.TOP or Gravity.START
        input.setTextColor(Color.parseColor(NAVY))
        input.textSize = 14f
        input.background = rounded(Color.parseColor("#F1ECF8"), context.dp(14).toFloat())
        input.setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))

        val container = FrameLayout(context)
        container.setPadding(context.dp(20), context.dp(12), context.dp(20), 0)
        container.addView(input)

        AlertDialog.Builder(context)
            .setTitle("Edit post")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->

                val newText = input.text.toString().trim()

                if (newText.isEmpty() && !isPhoto) {
                    Toast.makeText(
                        context,
                        "Post cannot be empty.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val field = if (isPhoto) "caption" else "textContent"

                SupabaseDatabase.getInstance()
                    .collection("posts")
                    .document(post.postId)
                    .update(
                        field, newText,
                        "edited", true,
                        "editedAt", FieldValue.serverTimestamp()
                    )
                    .addOnSuccessListener {
                        Toast.makeText(context, "Post updated.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            context,
                            "Failed to update: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    // =========================================================
    // COMMENTS
    // =========================================================

    private data class CommentItem(
        val id: String,
        val userId: String,
        val author: String,
        val text: String,
        val time: String
    )

    private class CommentAdapter(
        private val context: Context,
        private val myUid: String,
        private val onLongPress: (CommentItem) -> Unit
    ) : RecyclerView.Adapter<CommentAdapter.Holder>() {

        var items: List<CommentItem> = emptyList()

        private val db = SupabaseDatabase.getInstance()
        private val avatarCache = mutableMapOf<String, String>()
        private val requested = mutableSetOf<String>()

        class Holder(
            view: View,
            val initialsView: TextView,
            val avatarImage: ImageView,
            val author: TextView,
            val text: TextView,
            val time: TextView
        ) : RecyclerView.ViewHolder(view)

        // Kuhaon ang karon nga profile picture sa nag-comment
        private fun loadAvatar(userId: String) {

            if (userId.isBlank() || !requested.add(userId)) {
                return
            }

            db.collection("users").document(userId).get()
                .addOnSuccessListener { user ->

                    val own =
                        user.getString("profileImageUrl")
                            ?: user.getString("avatarUrl")
                            ?: ""

                    if (own.isNotBlank()) {
                        avatarCache[userId] = own
                        notifyDataSetChanged()
                        return@addOnSuccessListener
                    }

                    // Admin: gamiton ang picture sa iyang organization
                    val orgId = user.getString("organizationId") ?: ""

                    if (orgId.isBlank()) {
                        return@addOnSuccessListener
                    }

                    db.collection("organizations").document(orgId).get()
                        .addOnSuccessListener { org ->

                            val url = org.getString("avatarUrl") ?: ""

                            if (url.isNotBlank()) {
                                avatarCache[userId] = url
                                notifyDataSetChanged()
                            }
                        }
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {

            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            row.setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))

            // Avatar: initials + picture (circle)
            val avatarBox = FrameLayout(context)
            avatarBox.layoutParams = LinearLayout.LayoutParams(context.dp(36), context.dp(36))

            val initialsView = TextView(context)
            initialsView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            initialsView.gravity = Gravity.CENTER
            initialsView.setTextColor(Color.WHITE)
            initialsView.textSize = 12f
            initialsView.setTypeface(null, Typeface.BOLD)
            initialsView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3B176A"))
            }

            val avatarImage = ImageView(context)
            avatarImage.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            avatarImage.scaleType = ImageView.ScaleType.CENTER_CROP
            avatarImage.visibility = View.GONE

            avatarBox.addView(initialsView)
            avatarBox.addView(avatarImage)

            val column = LinearLayout(context)
            column.orientation = LinearLayout.VERTICAL
            val columnParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            columnParams.marginStart = context.dp(10)
            column.layoutParams = columnParams

            val bubble = LinearLayout(context)
            bubble.orientation = LinearLayout.VERTICAL
            bubble.background = rounded(Color.parseColor("#F1ECF8"), context.dp(16).toFloat())
            bubble.setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            bubble.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val author = TextView(context)
            author.setTextColor(Color.parseColor(NAVY))
            author.textSize = 12f
            author.setTypeface(null, Typeface.BOLD)

            val text = TextView(context)
            text.setTextColor(Color.parseColor("#3B176A"))
            text.textSize = 13f

            bubble.addView(author)
            bubble.addView(text)

            val time = TextView(context)
            time.setTextColor(Color.parseColor(GRAY))
            time.textSize = 10f
            time.setPadding(context.dp(6), context.dp(3), 0, 0)

            column.addView(bubble)
            column.addView(time)

            row.addView(avatarBox)
            row.addView(column)

            return Holder(row, initialsView, avatarImage, author, text, time)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {

            val item = items[position]

            holder.initialsView.text = initials(item.author)
            holder.author.text = item.author
            holder.text.text = item.text
            holder.time.text = item.time

            val url = avatarCache[item.userId].orEmpty()

            if (url.isNotBlank()) {

                holder.avatarImage.visibility = View.VISIBLE
                holder.initialsView.visibility = View.GONE

                Glide.with(holder.itemView.context)
                    .load(url)
                    .circleCrop()
                    .into(holder.avatarImage)

            } else {

                Glide.with(holder.itemView.context).clear(holder.avatarImage)

                holder.avatarImage.visibility = View.GONE
                holder.initialsView.visibility = View.VISIBLE

                loadAvatar(item.userId)
            }

            holder.itemView.setOnLongClickListener {
                if (item.userId == myUid) {
                    onLongPress(item)
                    true
                } else {
                    false
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun initials(name: String): String {

        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
            parts.size == 1 -> parts[0].take(2)
            else -> "?"
        }.uppercase()
    }

    private fun commentTime(date: Date?): String {

        if (date == null) return "Just now"

        val diff = System.currentTimeMillis() - date.time
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diff < minute -> "Just now"
            diff < hour -> "${diff / minute}m ago"
            diff < day -> "${diff / hour}h ago"
            diff < 7 * day -> "${diff / day}d ago"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
        }
    }

    fun showComments(
        context: Context,
        post: FeedPostModel
    ) {

        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(context, "Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        if (post.postId.isBlank()) return

        val db = SupabaseDatabase.getInstance()
        val postRef = db.collection("posts").document(post.postId)

        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.background = rounded(Color.WHITE, context.dp(24).toFloat(), true)
        root.minimumHeight = (context.resources.displayMetrics.heightPixels * 0.7f).toInt()

        root.addView(handle(context))

        val title = TextView(context)
        title.text = "Comments"
        title.setTextColor(Color.parseColor(NAVY))
        title.textSize = 16f
        title.setTypeface(null, Typeface.BOLD)
        title.setPadding(context.dp(16), 0, context.dp(16), context.dp(8))
        root.addView(title)

        val divider = View(context)
        divider.setBackgroundColor(Color.parseColor("#E1E8F2"))
        divider.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1)
        )
        root.addView(divider)

        val empty = TextView(context)
        empty.text = "No comments yet. Be the first to comment!"
        empty.setTextColor(Color.parseColor(GRAY))
        empty.textSize = 12.5f
        empty.gravity = Gravity.CENTER
        empty.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        root.addView(empty)

        val adapter = CommentAdapter(context, uid) { comment ->

            AlertDialog.Builder(context)
                .setTitle("Delete comment?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->

                    val batch = db.batch()
                    batch.delete(postRef.collection("comments").document(comment.id))
                    batch.update(postRef, "commentCount", FieldValue.increment(-1))
                    batch.commit()
                }
                .show()
        }

        val list = RecyclerView(context)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        list.visibility = View.GONE
        root.addView(list)

        // ----- input row -----
        val inputRow = LinearLayout(context)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(12))

        val input = EditText(context)
        input.hint = "Write a comment..."
        input.setHintTextColor(Color.parseColor("#6B5C7B"))
        input.setTextColor(Color.parseColor(NAVY))
        input.textSize = 13f
        input.maxLines = 4
        input.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.background = rounded(Color.parseColor("#F1ECF8"), context.dp(22).toFloat())
        input.setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        input.layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )

        val send = TextView(context)
        send.text = "Send"
        send.setTextColor(Color.WHITE)
        send.textSize = 12.5f
        send.setTypeface(null, Typeface.BOLD)
        send.gravity = Gravity.CENTER
        send.background = rounded(Color.parseColor(BLUE), context.dp(22).toFloat())
        send.setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        val sendParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        sendParams.marginStart = context.dp(8)
        send.layoutParams = sendParams

        inputRow.addView(input)
        inputRow.addView(send)
        root.addView(inputRow)

        send.setOnClickListener {

            val text = input.text.toString().trim()

            if (text.isEmpty()) return@setOnClickListener

            send.isEnabled = false
            send.alpha = 0.5f

            db.collection("users").document(uid).get()
                .addOnCompleteListener { task ->

                    val name =
                        (if (task.isSuccessful) task.result?.getString("fullName") else null)
                            ?: auth.currentUser?.displayName
                            ?: "User"

                    val commentRef = postRef.collection("comments").document()

                    val batch = db.batch()

                    batch.set(
                        commentRef,
                        hashMapOf(
                            "userId" to uid,
                            "authorName" to name,
                            "text" to text,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )

                    batch.update(postRef, "commentCount", FieldValue.increment(1))

                    batch.commit()
                        .addOnSuccessListener {

                            input.text.clear()

                            // =============================================
                            // NOTIFY ORGANIZATION ADMIN
                            // =============================================

                            if (
                                uid != post.createdBy &&
                                post.organizationId.isNotBlank()
                            ) {

                                val preview =
                                    if (text.length > 70) {
                                        text.take(70) + "..."
                                    } else {
                                        text
                                    }

                                AdminNotificationHelper.create(
                                    organizationId =
                                        post.organizationId,

                                    organizationName =
                                        post.organizationName,

                                    title =
                                        "New Comment on Your Post",

                                    message =
                                        "$name commented: $preview",

                                    type =
                                        "comment",

                                    referenceId =
                                        post.postId,

                                    postId =
                                        post.postId,

                                    studentId =
                                        uid
                                )
                            }
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(
                                context,
                                "Failed to comment: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .addOnCompleteListener {
                            send.isEnabled = true
                            send.alpha = 1f
                        }
                }
        }

        val registration =
            postRef.collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        return@addSnapshotListener
                    }

                    adapter.items = snapshot.documents.map { document ->
                        CommentItem(
                            id = document.id,
                            userId = document.getString("userId") ?: "",
                            author = document.getString("authorName") ?: "User",
                            text = document.getString("text") ?: "",
                            time = commentTime(document.getTimestamp("createdAt")?.toDate())
                        )
                    }

                    adapter.notifyDataSetChanged()

                    val hasItems = adapter.items.isNotEmpty()

                    list.visibility = if (hasItems) View.VISIBLE else View.GONE
                    empty.visibility = if (hasItems) View.GONE else View.VISIBLE
                    title.text =
                        if (hasItems) "Comments (${adapter.items.size})" else "Comments"

                    if (hasItems) {
                        list.scrollToPosition(adapter.items.size - 1)
                    }
                }

        dialog.setOnDismissListener { registration.remove() }

        prepareSheet(dialog)
        dialog.setContentView(root)
        dialog.show()
    }
}