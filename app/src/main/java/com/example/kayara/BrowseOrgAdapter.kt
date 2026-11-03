package com.example.kayara

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class BrowseOrgAdapter(
    private var fullList: List<OrganizationModel>,
    private val onJoinClick: (OrganizationModel) -> Unit,
    private val onOrgClick: ((OrganizationModel) -> Unit)? = null
) : RecyclerView.Adapter<BrowseOrgAdapter.BrowseViewHolder>() {

    private var displayedList: List<OrganizationModel> =
        fullList

    // FIX: combine category filter + search
    private var currentCategory =
        "all"

    private var currentQuery =
        ""

    // =========================================================
    // VIEW HOLDER
    // =========================================================

    inner class BrowseViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val tvAvatar: TextView =
            itemView.findViewById(
                R.id.tvBrowseAvatar
            )

        val imgAvatar: ImageView =
            itemView.findViewById(
                R.id.imgBrowseAvatar
            )

        val tvName: TextView =
            itemView.findViewById(
                R.id.tvBrowseOrgName
            )

        val tvMeta: TextView =
            itemView.findViewById(
                R.id.tvBrowseOrgMeta
            )

        val tvStatus: TextView =
            itemView.findViewById(
                R.id.tvBrowseStatus
            )
    }

    // =========================================================
    // CREATE VIEW HOLDER
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BrowseViewHolder {

        val view =
            LayoutInflater
                .from(
                    parent.context
                )
                .inflate(
                    R.layout.item_browse_org,
                    parent,
                    false
                )

        return BrowseViewHolder(
            view
        )
    }

    // =========================================================
    // BIND
    // =========================================================

    override fun onBindViewHolder(
        holder: BrowseViewHolder,
        position: Int
    ) {

        val org =
            displayedList[position]

        // =====================================================
        // ORGANIZATION AVATAR
        // =====================================================

        holder.tvAvatar.text =
            org.shortCode
                .ifBlank {
                    getInitials(
                        org.orgName
                    )
                }
                .take(4)
                .uppercase()

        holder.tvAvatar.background
            ?.mutate()
            ?.setTint(
                try {

                    Color.parseColor(
                        org.colorStart
                    )

                } catch (_: Exception) {

                    Color.parseColor(
                        "#7C3AED"
                    )
                }
            )

        val avatarUrl =
            org.avatarUrl.trim()

        if (
            avatarUrl.isNotBlank()
        ) {

            holder.imgAvatar.visibility =
                View.VISIBLE

            holder.tvAvatar.visibility =
                View.GONE

            Glide.with(
                holder.itemView.context
            )
                .load(
                    avatarUrl
                )
                .circleCrop()
                .into(
                    holder.imgAvatar
                )

        } else {

            Glide.with(
                holder.itemView.context
            )
                .clear(
                    holder.imgAvatar
                )

            holder.imgAvatar
                .setImageDrawable(
                    null
                )

            holder.imgAvatar.visibility =
                View.GONE

            holder.tvAvatar.visibility =
                View.VISIBLE
        }

        // =====================================================
        // ORGANIZATION INFO
        // =====================================================

        holder.tvName.text =
            org.orgName

        holder.tvMeta.text =
            "${org.memberCount} members"

        // =====================================================
        // CARD CLICK -> ORGANIZATION PROFILE
        // =====================================================

        holder.itemView
            .setOnClickListener {

                openProfile(
                    holder.itemView.context,
                    org
                )
            }

        // =====================================================
        // STATUS
        // =====================================================

        when (
            org.category
        ) {

            "joined" -> {

                holder.tvStatus.text =
                    "JOINED"

                holder.tvStatus
                    .setBackgroundResource(
                        R.drawable.bg_joined_tag
                    )

                holder.tvStatus
                    .setTextColor(
                        holder.itemView
                            .context
                            .getColor(
                                R.color.teal_deep_text
                            )
                    )

                holder.tvStatus
                    .setOnClickListener {

                        openProfile(
                            holder.itemView.context,
                            org
                        )
                    }
            }

            "pending" -> {

                holder.tvStatus.text =
                    "PENDING"

                holder.tvStatus
                    .setBackgroundResource(
                        R.drawable.bg_pending_tag
                    )

                holder.tvStatus
                    .setTextColor(
                        holder.itemView
                            .context
                            .getColor(
                                R.color.pending_text
                            )
                    )

                holder.tvStatus
                    .setOnClickListener {

                        openProfile(
                            holder.itemView.context,
                            org
                        )
                    }
            }

            else -> {

                if (
                    org.recruitmentStatus == "closed" ||
                    org.recruitmentStatus == "full"
                ) {

                    holder.tvStatus.text =
                        if (
                            org.recruitmentStatus == "full"
                        ) {
                            "FULL"
                        } else {
                            "CLOSED"
                        }

                    holder.tvStatus
                        .setBackgroundResource(
                            R.drawable.bg_role_member
                        )

                    holder.tvStatus
                        .setTextColor(
                            holder.itemView
                                .context
                                .getColor(
                                    R.color.coral_text
                                )
                        )

                    holder.tvStatus
                        .setOnClickListener {

                            onJoinClick(
                                org
                            )
                        }

                } else {

                    holder.tvStatus.text =
                        "JOIN"

                    holder.tvStatus
                        .setBackgroundResource(
                            R.drawable.bg_join_btn
                        )

                    holder.tvStatus
                        .setTextColor(
                            holder.itemView
                                .context
                                .getColor(
                                    R.color.white
                                )
                        )

                    holder.tvStatus
                        .setOnClickListener {

                            onJoinClick(
                                org
                            )
                        }
                }
            }
        }
    }

    // =========================================================
    // OPEN PROFILE
    // =========================================================

    private fun openProfile(
        context: Context,
        org: OrganizationModel
    ) {

        if (
            onOrgClick != null
        ) {

            onOrgClick.invoke(
                org
            )

            return
        }

        val intent =
            Intent(
                context,
                OrganizationProfileActivity::class.java
            )

        intent.putExtra(
            "organizationId",
            org.organizationId
        )

        intent.putExtra(
            "organizationName",
            org.orgName
        )

        intent.putExtra(
            "shortCode",
            org.shortCode
        )

        context.startActivity(
            intent
        )
    }

    // =========================================================
    // COUNT
    // =========================================================

    override fun getItemCount(): Int {

        return displayedList.size
    }

    // =========================================================
    // FILTERS
    // =========================================================

    private fun applyFilters() {

        displayedList =
            fullList
                .filter { org ->

                    currentCategory == "all" ||
                            org.category ==
                            currentCategory
                }
                .filter { org ->

                    currentQuery.isBlank() ||
                            org.orgName.contains(
                                currentQuery,
                                ignoreCase = true
                            )
                }

        notifyDataSetChanged()
    }

    fun updateList(
        newList: List<OrganizationModel>
    ) {

        fullList =
            newList

        applyFilters()
    }

    fun filter(
        category: String
    ) {

        currentCategory =
            category

        applyFilters()
    }

    fun search(
        query: String
    ) {

        currentQuery =
            query

        applyFilters()
    }

    // =========================================================
    // INITIALS
    // =========================================================

    private fun getInitials(
        name: String
    ): String {

        val words =
            name.trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        return when {

            words.isEmpty() ->
                "ORG"

            words.size == 1 ->
                words[0]
                    .take(3)
                    .uppercase()

            else ->
                words
                    .take(3)
                    .map {
                        it.first()
                    }
                    .joinToString("")
                    .uppercase()
        }
    }
}