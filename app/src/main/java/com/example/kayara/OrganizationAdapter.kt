package com.example.kayara

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class OrganizationAdapter(
    private var fullList: List<OrganizationModel>,
    private val onOrgClick: (OrganizationModel) -> Unit
) : RecyclerView.Adapter<OrganizationAdapter.OrgViewHolder>() {

    private var displayedList: List<OrganizationModel> =
        fullList


    inner class OrgViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val ivAvatar: ImageView =
            itemView.findViewById(
                R.id.ivOrgAvatar
            )

        val tvAvatar: TextView =
            itemView.findViewById(
                R.id.tvOrgAvatar
            )

        val tvName: TextView =
            itemView.findViewById(
                R.id.tvOrgName
            )

        val tvMeta: TextView =
            itemView.findViewById(
                R.id.tvOrgMeta
            )

        val tvBadge: TextView =
            itemView.findViewById(
                R.id.tvOrgBadge
            )
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): OrgViewHolder {

        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(
                    R.layout.item_organization,
                    parent,
                    false
                )

        return OrgViewHolder(view)
    }


    override fun onBindViewHolder(
        holder: OrgViewHolder,
        position: Int
    ) {

        val org =
            displayedList[position]


        // =====================================================
        // ORGANIZATION PROFILE IMAGE
        // =====================================================

        if (
            org.avatarUrl.isNotBlank()
        ) {

            holder.ivAvatar.visibility =
                View.VISIBLE

            holder.tvAvatar.visibility =
                View.GONE

            Glide
                .with(holder.itemView.context)
                .load(org.avatarUrl)
                .circleCrop()
                .into(holder.ivAvatar)

        } else {

            holder.ivAvatar.visibility =
                View.GONE

            holder.tvAvatar.visibility =
                View.VISIBLE

            holder.tvAvatar.text =
                org.shortCode.ifBlank {
                    "ORG"
                }

            try {

                holder.tvAvatar
                    .background
                    .setTint(
                        Color.parseColor(
                            org.colorStart
                        )
                    )

            } catch (_: Exception) {

                holder.tvAvatar
                    .background
                    .setTint(
                        Color.parseColor(
                            "#7C3AED"
                        )
                    )
            }
        }


        // =====================================================
        // ORGANIZATION NAME
        // =====================================================

        holder.tvName.text =
            org.orgName


        // =====================================================
        // MEMBER COUNT + ADVISERS
        // =====================================================

        /*
         * adviserName1 = new primary adviser field
         *
         * adviserName = old field for compatibility
         *
         * adviserName2 = optional second adviser
         */

        val adviser1 =
            org.adviserName1
                .ifBlank {
                    org.adviserName
                }
                .trim()

        val adviser2 =
            org.adviserName2
                .trim()


        val adviserText =
            when {

                adviser1.isNotBlank() &&
                        adviser2.isNotBlank() -> {

                    "Advisers: $adviser1 & $adviser2"
                }

                adviser1.isNotBlank() -> {

                    "Adviser: $adviser1"
                }

                adviser2.isNotBlank() -> {

                    /*
                     * Safety fallback in case Adviser 1
                     * is empty but Adviser 2 has data.
                     */
                    "Adviser: $adviser2"
                }

                else -> {

                    "No adviser assigned"
                }
            }


        val memberText =
            if (
                org.memberCount == 1
            ) {

                "1 member"

            } else {

                "${org.memberCount} members"
            }


        holder.tvMeta.text =
            "$memberText · $adviserText"


        // =====================================================
        // STATUS BADGE
        // =====================================================

        when (
            org.category
        ) {

            // -------------------------------------------------
            // NEW ORGANIZATION REQUEST
            // -------------------------------------------------

            "new_request" -> {

                holder.tvBadge.visibility =
                    View.VISIBLE

                holder.tvBadge.text =
                    "NEW REQUEST"

                holder.tvBadge
                    .setBackgroundResource(
                        R.drawable.bg_tag_new
                    )

                holder.tvBadge
                    .setTextColor(
                        holder.itemView
                            .context
                            .getColor(
                                R.color.coral_text
                            )
                    )
            }


            // -------------------------------------------------
            // RENEWAL DUE
            // -------------------------------------------------

            "renewal_due" -> {

                holder.tvBadge.visibility =
                    View.VISIBLE

                holder.tvBadge.text =
                    "RENEWAL DUE"

                holder.tvBadge
                    .setBackgroundResource(
                        R.drawable.bg_tag_pending
                    )

                holder.tvBadge
                    .setTextColor(
                        holder.itemView
                            .context
                            .getColor(
                                R.color.amber_text
                            )
                    )
            }


            // -------------------------------------------------
            // REVIEWING RENEWAL
            // -------------------------------------------------

            "reviewing_renewal" -> {

                holder.tvBadge.visibility =
                    View.VISIBLE

                holder.tvBadge.text =
                    "REVIEWING RENEWAL"

                holder.tvBadge
                    .setBackgroundResource(
                        R.drawable.bg_tag_pending
                    )

                holder.tvBadge
                    .setTextColor(
                        holder.itemView
                            .context
                            .getColor(
                                R.color.amber_text
                            )
                    )
            }


            // -------------------------------------------------
            // NORMAL / APPROVED
            // -------------------------------------------------

            else -> {

                holder.tvBadge.visibility =
                    View.GONE
            }
        }


        // =====================================================
        // CLICK ORGANIZATION
        // =====================================================

        holder.itemView
            .setOnClickListener {

                onOrgClick(org)
            }


        // Profile image clickable too
        holder.ivAvatar
            .setOnClickListener {

                onOrgClick(org)
            }


        // Fallback avatar clickable too
        holder.tvAvatar
            .setOnClickListener {

                onOrgClick(org)
            }
    }


    override fun getItemCount(): Int {

        return displayedList.size
    }


    // =====================================================
    // UPDATE LIST
    // =====================================================

    fun updateList(
        newList: List<OrganizationModel>
    ) {

        fullList =
            newList

        displayedList =
            newList

        notifyDataSetChanged()
    }


    // =====================================================
    // FILTER
    // =====================================================

    fun filter(
        category: String
    ) {

        displayedList =
            when (
                category
            ) {

                "new_request" -> {

                    fullList.filter {
                        it.category ==
                                "new_request"
                    }
                }


                "renewal_due" -> {

                    fullList.filter {

                        it.category ==
                                "renewal_due" ||

                                it.category ==
                                "reviewing_renewal"
                    }
                }


                else -> {

                    fullList
                }
            }

        notifyDataSetChanged()
    }


    // =====================================================
    // SEARCH
    // =====================================================

    fun search(
        query: String
    ) {

        displayedList =
            if (
                query.isBlank()
            ) {

                fullList

            } else {

                fullList.filter {

                    it.orgName.contains(
                        query,
                        ignoreCase = true
                    ) ||

                            it.shortCode.contains(
                                query,
                                ignoreCase = true
                            ) ||

                            it.adviserName1.contains(
                                query,
                                ignoreCase = true
                            ) ||

                            it.adviserName2.contains(
                                query,
                                ignoreCase = true
                            ) ||

                            it.adviserName.contains(
                                query,
                                ignoreCase = true
                            )
                }
            }

        notifyDataSetChanged()
    }
}