package com.example.kayara

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.kayara.data.ManagedOrganization
import com.example.kayara.data.SupabaseDatabase

object OrganizationAccess {
    fun show(activity: Activity) {
        SupabaseDatabase.getInstance().accountAccess().addOnSuccessListener(activity) { access ->
            when (access.organizations.size) {
                0 -> Toast.makeText(activity, R.string.manager_access_missing, Toast.LENGTH_LONG).show()
                1 -> open(activity, access.organizations.single())
                else -> AlertDialog.Builder(activity).setTitle(R.string.manage_organization)
                    .setItems(access.organizations.map { it.name }.toTypedArray()) { _, index ->
                        open(activity, access.organizations[index])
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
        }.addOnFailureListener(activity) {
            Toast.makeText(activity, it.message ?: activity.getString(R.string.access_load_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun open(activity: Activity, organization: ManagedOrganization) {
        activity.startActivity(Intent(activity, AdminDashboardActivity::class.java)
            .putExtra("organizationId", organization.id).putExtra("organizationName", organization.name))
    }
}
