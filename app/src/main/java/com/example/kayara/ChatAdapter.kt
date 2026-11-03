package com.example.kayara

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.kayara.data.SupabaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUid: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MINE = 1
        private const val TYPE_OTHER = 2
    }

    private val items = mutableListOf<ChatMessageModel>()

    private val db = SupabaseDatabase.getInstance()

    // senderId -> karon nga profile picture
    private val avatarCache = mutableMapOf<String, String>()
    private val requested = mutableSetOf<String>()

    // =========================================================
    // VIEW HOLDERS
    // =========================================================

    inner class MineViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val tvText: TextView =
            itemView.findViewById(R.id.tvChatText)

        val tvTime: TextView =
            itemView.findViewById(R.id.tvChatTime)
    }

    inner class OtherViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val tvAvatar: TextView =
            itemView.findViewById(R.id.tvChatAvatar)

        val imgAvatar: ImageView =
            itemView.findViewById(R.id.imgChatAvatar)

        val tvSender: TextView =
            itemView.findViewById(R.id.tvChatSender)

        val tvRole: TextView =
            itemView.findViewById(R.id.tvChatRole)

        val tvText: TextView =
            itemView.findViewById(R.id.tvChatText)

        val tvTime: TextView =
            itemView.findViewById(R.id.tvChatTime)
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    override fun getItemViewType(position: Int): Int {

        return if (items[position].senderId == currentUid) {
            TYPE_MINE
        } else {
            TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        val inflater =
            LayoutInflater.from(parent.context)

        return if (viewType == TYPE_MINE) {

            MineViewHolder(
                inflater.inflate(
                    R.layout.item_chat_mine,
                    parent,
                    false
                )
            )

        } else {

            OtherViewHolder(
                inflater.inflate(
                    R.layout.item_chat_other,
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {

        val message = items[position]

        if (holder is MineViewHolder) {

            holder.tvText.text = message.text
            holder.tvTime.text = formatTime(message.timeMillis)

        } else if (holder is OtherViewHolder) {

            holder.tvText.text = message.text
            holder.tvTime.text = formatTime(message.timeMillis)
            holder.tvSender.text = message.senderName

            // Role tag (Admin / officer position)
            if (message.senderRole.isNotBlank()) {

                holder.tvRole.visibility = View.VISIBLE
                holder.tvRole.text = message.senderRole.uppercase()

            } else {

                holder.tvRole.visibility = View.GONE
            }

            // Avatar: karon nga profile picture, initials kung wala
            holder.tvAvatar.text = getInitials(message.senderName)

            val url =
                avatarCache[message.senderId]
                    ?: message.senderPhotoUrl

            if (url.isNotBlank()) {

                holder.imgAvatar.visibility = View.VISIBLE
                holder.tvAvatar.visibility = View.GONE

                Glide.with(holder.imgAvatar.context)
                    .load(url)
                    .circleCrop()
                    .into(holder.imgAvatar)

            } else {

                Glide.with(holder.imgAvatar.context)
                    .clear(holder.imgAvatar)

                holder.imgAvatar.setImageDrawable(null)
                holder.imgAvatar.visibility = View.GONE
                holder.tvAvatar.visibility = View.VISIBLE
            }

            // Kuhaon ang karon nga picture (kausa ra matag sender)
            loadAvatar(
                message.senderId,
                message.senderRole
            )
        }
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<ChatMessageModel>) {

        items.clear()
        items.addAll(newItems)

        notifyDataSetChanged()
    }

    // =========================================================
    // CURRENT PROFILE PICTURE
    // 1. Personal nga picture sa user (profileImageUrl)
    // 2. Admin nga walay personal: picture sa iyang organization
    // =========================================================

    private fun loadAvatar(
        senderId: String,
        role: String
    ) {

        if (senderId.isBlank() || !requested.add(senderId)) {
            return
        }

        db.collection("users")
            .document(senderId)
            .get()
            .addOnSuccessListener { user ->

                val own =
                    user.getString("profileImageUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?: user.getString("avatarUrl")
                            ?.takeIf { it.isNotBlank() }

                if (own != null) {

                    avatarCache[senderId] = own
                    notifyDataSetChanged()

                    return@addOnSuccessListener
                }

                if (!role.equals("Admin", ignoreCase = true)) {
                    return@addOnSuccessListener
                }

                val orgId =
                    user.getString("organizationId") ?: ""

                if (orgId.isBlank()) {
                    return@addOnSuccessListener
                }

                db.collection("organizations")
                    .document(orgId)
                    .get()
                    .addOnSuccessListener { org ->

                        val orgAvatar =
                            org.getString("avatarUrl") ?: ""

                        if (orgAvatar.isNotBlank()) {

                            avatarCache[senderId] = orgAvatar
                            notifyDataSetChanged()
                        }
                    }
            }
            .addOnFailureListener {
                // Ayaw i-cache ang failure, para makasulay balik
                requested.remove(senderId)
            }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private fun formatTime(timeMillis: Long): String {

        if (timeMillis <= 0L) {
            return "Just now"
        }

        val now = Calendar.getInstance()
        val then = Calendar.getInstance()

        then.timeInMillis = timeMillis

        val sameDay =
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

        val pattern =
            if (sameDay) "h:mm a" else "MMM d, h:mm a"

        return SimpleDateFormat(
            pattern,
            Locale.getDefault()
        ).format(Date(timeMillis))
    }

    private fun getInitials(name: String): String {

        val parts =
            name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

        return when {
            parts.size >= 2 ->
                "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 ->
                parts[0].take(2).uppercase()
            else -> "ST"
        }
    }
}