package com.example.kayara

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MemberAdapter(
    private var fullList: MutableList<MemberModel>,
    private val onApprove: (Int) -> Unit,
    private val onReject: (Int) -> Unit,

    // Optional officer assignment
    private val onAssignOfficer: ((Int) -> Unit)? = null,

    // Optional submitted documents
    private val onViewDocuments: ((Int) -> Unit)? = null,

    // Optional row click
    private val onItemClick: ((Int) -> Unit)? = null,

    // NEW: active member/officer removal
    private val onRemoveMember: ((Int) -> Unit)? = null

) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    private var displayedList: MutableList<MemberModel> =
        fullList.toMutableList()

    // =========================================================
    // VIEW HOLDER
    // =========================================================

    inner class MemberViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvAvatar: TextView =
            itemView.findViewById(
                R.id.tvMemberAvatar
            )

        val imgAvatar: ImageView =
            itemView.findViewById(
                R.id.imgMemberAvatar
            )

        val tvName: TextView =
            itemView.findViewById(
                R.id.tvMemberName
            )

        val tvDetails: TextView =
            itemView.findViewById(
                R.id.tvMemberDetails
            )

        val tvRoleTag: TextView =
            itemView.findViewById(
                R.id.tvMemberRoleTag
            )

        val tvDocs: TextView =
            itemView.findViewById(
                R.id.tvMemberDocs
            )

        val tvNote: TextView =
            itemView.findViewById(
                R.id.tvApplicationNote
            )

        val btnApprove: ImageView =
            itemView.findViewById(
                R.id.btnApproveMember
            )

        val btnReject: ImageView =
            itemView.findViewById(
                R.id.btnRejectMember
            )

        val btnMemberMenu: ImageView =
            itemView.findViewById(
                R.id.btnMemberMenu
            )
    }

    // =========================================================
    // CREATE
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MemberViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_member,
                    parent,
                    false
                )

        return MemberViewHolder(view)
    }

    // =========================================================
    // BIND
    // =========================================================

    override fun onBindViewHolder(
        holder: MemberViewHolder,
        position: Int
    ) {

        val member =
            displayedList[position]

        // =====================================================
        // AVATAR
        // =====================================================

        holder.tvAvatar.text =
            getInitials(
                member.fullName
            )

        try {

            holder.tvAvatar.background.setTint(
                Color.parseColor(
                    member.avatarColorStart
                )
            )

        } catch (_: Exception) {

            holder.tvAvatar.background.setTint(
                Color.parseColor(
                    "#3B176A"
                )
            )
        }

        if (
            member.avatarUrl.isNotBlank()
        ) {

            holder.imgAvatar.visibility =
                View.VISIBLE

            holder.tvAvatar.visibility =
                View.GONE

            Glide.with(
                holder.imgAvatar.context
            )
                .load(
                    member.avatarUrl
                )
                .circleCrop()
                .into(
                    holder.imgAvatar
                )

        } else {

            Glide.with(
                holder.imgAvatar.context
            )
                .clear(
                    holder.imgAvatar
                )

            holder.imgAvatar.setImageDrawable(
                null
            )

            holder.imgAvatar.visibility =
                View.GONE

            holder.tvAvatar.visibility =
                View.VISIBLE
        }

        // =====================================================
        // MEMBER INFO
        // =====================================================

        holder.tvName.text =
            member.fullName

        holder.tvDetails.text =
            member.details

        // =====================================================
        // APPLICATION NOTE
        // =====================================================

        if (
            member.applicationNote.isNotBlank() &&
            member.role == "pending"
        ) {

            holder.tvNote.visibility =
                View.VISIBLE

            holder.tvNote.text =
                "💬 \"${member.applicationNote}\""

        } else {

            holder.tvNote.visibility =
                View.GONE
        }

        // =====================================================
        // DOCUMENTS
        // =====================================================

        if (
            onViewDocuments != null &&
            member.documents.isNotEmpty()
        ) {

            val count =
                member.documents.size

            holder.tvDocs.visibility =
                View.VISIBLE

            holder.tvDocs.text =
                "📎 $count document${if (count == 1) "" else "s"} submitted · View"

            holder.tvDocs.setOnClickListener {

                val adapterPosition =
                    holder.bindingAdapterPosition

                if (
                    adapterPosition !=
                    RecyclerView.NO_POSITION
                ) {

                    onViewDocuments.invoke(
                        adapterPosition
                    )
                }
            }

        } else {

            holder.tvDocs.visibility =
                View.GONE

            holder.tvDocs.setOnClickListener(
                null
            )
        }

        // =====================================================
        // ROW CLICK
        // =====================================================

        if (
            onItemClick != null
        ) {

            holder.itemView
                .setOnClickListener {

                    val adapterPosition =
                        holder.bindingAdapterPosition

                    if (
                        adapterPosition !=
                        RecyclerView.NO_POSITION
                    ) {

                        onItemClick.invoke(
                            adapterPosition
                        )
                    }
                }

            holder.itemView.isClickable =
                true

        } else {

            holder.itemView
                .setOnClickListener(
                    null
                )

            holder.itemView.isClickable =
                false
        }

        // =====================================================
        // ROLE / STATUS
        // =====================================================

        val arrow =
            if (
                onAssignOfficer != null &&
                member.role != "pending"
            ) {

                " ▾"

            } else {

                ""
            }

        when (
            member.role.lowercase()
        ) {

            "officer" -> {

                holder.tvRoleTag.text =
                    member.position
                        .ifBlank {
                            "OFFICER"
                        }
                        .uppercase() +
                            arrow

                holder.tvRoleTag
                    .setBackgroundResource(
                        R.drawable.bg_role_officer
                    )

                holder.tvRoleTag
                    .setTextColor(
                        holder.itemView.context
                            .getColor(
                                R.color.blue_deep
                            )
                    )

                holder.btnApprove.visibility =
                    View.GONE

                holder.btnReject.visibility =
                    View.GONE

                holder.btnMemberMenu.visibility =
                    if (
                        onRemoveMember != null
                    ) {

                        View.VISIBLE

                    } else {

                        View.GONE
                    }
            }

            "pending" -> {

                holder.tvRoleTag.text =
                    "PENDING"

                holder.tvRoleTag
                    .setBackgroundResource(
                        R.drawable.bg_tag_pending
                    )

                holder.tvRoleTag
                    .setTextColor(
                        holder.itemView.context
                            .getColor(
                                R.color.amber_text
                            )
                    )

                holder.btnApprove.visibility =
                    View.VISIBLE

                holder.btnReject.visibility =
                    View.VISIBLE

                /*
                 * Pending applicant uses Approve/Reject,
                 * therefore no menu.
                 */
                holder.btnMemberMenu.visibility =
                    View.GONE
            }

            else -> {

                holder.tvRoleTag.text =
                    "MEMBER$arrow"

                holder.tvRoleTag
                    .setBackgroundResource(
                        R.drawable.bg_role_member
                    )

                holder.tvRoleTag
                    .setTextColor(
                        holder.itemView.context
                            .getColor(
                                R.color.text_gray
                            )
                    )

                holder.btnApprove.visibility =
                    View.GONE

                holder.btnReject.visibility =
                    View.GONE

                holder.btnMemberMenu.visibility =
                    if (
                        onRemoveMember != null
                    ) {

                        View.VISIBLE

                    } else {

                        View.GONE
                    }
            }
        }

        // =====================================================
        // ASSIGN OFFICER
        // =====================================================

        if (
            onAssignOfficer != null &&
            member.role != "pending"
        ) {

            holder.tvRoleTag
                .setOnClickListener {

                    val adapterPosition =
                        holder.bindingAdapterPosition

                    if (
                        adapterPosition !=
                        RecyclerView.NO_POSITION
                    ) {

                        onAssignOfficer.invoke(
                            adapterPosition
                        )
                    }
                }

            holder.tvRoleTag.isClickable =
                true

        } else {

            holder.tvRoleTag
                .setOnClickListener(
                    null
                )

            holder.tvRoleTag.isClickable =
                false
        }

        // =====================================================
        // APPROVE
        // =====================================================

        holder.btnApprove
            .setOnClickListener {

                val adapterPosition =
                    holder.bindingAdapterPosition

                if (
                    adapterPosition !=
                    RecyclerView.NO_POSITION
                ) {

                    onApprove(
                        adapterPosition
                    )
                }
            }

        // =====================================================
        // REJECT
        // =====================================================

        holder.btnReject
            .setOnClickListener {

                val adapterPosition =
                    holder.bindingAdapterPosition

                if (
                    adapterPosition !=
                    RecyclerView.NO_POSITION
                ) {

                    onReject(
                        adapterPosition
                    )
                }
            }

        // =====================================================
        // MEMBER MENU
        // =====================================================

        holder.btnMemberMenu
            .setOnClickListener { anchor ->

                val adapterPosition =
                    holder.bindingAdapterPosition

                if (
                    adapterPosition ==
                    RecyclerView.NO_POSITION
                ) {

                    return@setOnClickListener
                }

                showMemberMenu(
                    anchor = anchor,
                    position = adapterPosition
                )
            }
    }

    // =========================================================
    // MEMBER 3-DOT MENU
    // =========================================================

    private fun showMemberMenu(
        anchor: View,
        position: Int
    ) {

        val member =
            getMemberAt(
                position
            ) ?: return

        if (
            member.role == "pending"
        ) {

            return
        }

        val popupMenu =
            PopupMenu(
                anchor.context,
                anchor
            )

        /*
         * View Profile
         */
        if (
            onItemClick != null
        ) {

            popupMenu.menu.add(
                "View Profile"
            )
        }

        /*
         * Remove Member
         */
        if (
            onRemoveMember != null
        ) {

            popupMenu.menu.add(
                "Remove Member"
            )
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->

            when (
                menuItem.title.toString()
            ) {

                "View Profile" -> {

                    onItemClick?.invoke(
                        position
                    )

                    true
                }

                "Remove Member" -> {

                    onRemoveMember?.invoke(
                        position
                    )

                    true
                }

                else -> {

                    false
                }
            }
        }

        popupMenu.show()
    }

    // =========================================================
    // ITEM COUNT
    // =========================================================

    override fun getItemCount():
            Int {

        return displayedList.size
    }

    // =========================================================
    // GET MEMBER
    // =========================================================

    fun getMemberAt(
        position: Int
    ): MemberModel? {

        return if (
            position >= 0 &&
            position < displayedList.size
        ) {

            displayedList[position]

        } else {

            null
        }
    }

    // =========================================================
    // UPDATE LIST
    // =========================================================

    fun updateList(
        newList: MutableList<MemberModel>
    ) {

        fullList =
            newList

        displayedList =
            newList.toMutableList()

        notifyDataSetChanged()
    }

    // =========================================================
    // FILTER
    // =========================================================

    fun filter(
        role: String
    ) {

        displayedList =
            when (
                role
            ) {

                "officer" -> {

                    fullList
                        .filter {
                            it.role == "officer"
                        }
                        .toMutableList()
                }

                "pending" -> {

                    fullList
                        .filter {
                            it.role == "pending"
                        }
                        .toMutableList()
                }

                "member" -> {

                    fullList
                        .filter {
                            it.role == "member"
                        }
                        .toMutableList()
                }

                else -> {

                    fullList
                        .toMutableList()
                }
            }

        notifyDataSetChanged()
    }

    // =========================================================
    // SEARCH
    // =========================================================

    fun search(
        query: String
    ) {

        displayedList =
            if (
                query.isBlank()
            ) {

                fullList.toMutableList()

            } else {

                fullList
                    .filter {

                        it.fullName.contains(
                            query,
                            ignoreCase = true
                        ) ||

                                it.organizationName.contains(
                                    query,
                                    ignoreCase = true
                                ) ||

                                it.position.contains(
                                    query,
                                    ignoreCase = true
                                )
                    }
                    .toMutableList()
            }

        notifyDataSetChanged()
    }

    // =========================================================
    // INITIALS
    // =========================================================

    private fun getInitials(
        name: String
    ): String {

        val parts =
            name.trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        return when {

            parts.isEmpty() -> {

                "MB"
            }

            parts.size >= 2 -> {

                "${parts[0][0]}${parts[1][0]}"
                    .uppercase()
            }

            else -> {

                parts[0]
                    .take(2)
                    .uppercase()
            }
        }
    }
}