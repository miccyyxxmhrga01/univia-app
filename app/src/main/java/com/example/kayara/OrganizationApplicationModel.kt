package com.example.kayara

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

data class ApplicationProgress(val busy: Boolean = false, val message: String = "", val failed: Boolean = false, val complete: Boolean = false)

/** Uploads survive rotation; neither submission nor resubmission changes account roles. */
class OrganizationApplicationModel : ViewModel() {
    val progress = MutableLiveData(ApplicationProgress())
    val uris = arrayOfNulls<Uri>(5)
    val names = Array(5) { "" }
    val documents = arrayOfNulls<Map<String, Any?>>(5)
    var applicationId = UUID.randomUUID().toString()
    var editing = false
    var selectedSlot = 0
    @Volatile var busy = false
        private set

    companion object {
        val keys = listOf("police_clearance_lead_organizer", "proposed_constitution_bylaws", "list_of_members", "adviser_nominees", "annual_work_plan")
        val labels = listOf("Police Clearance of Lead Organizer", "Proposed Constitution and By-Laws",
            "List of Members (Minimum of 30 Members)", "Names of Two (2) Nominees for Organization Adviser", "Annual Work Plan of Activities")
    }

    fun submit(name: String, description: String) {
        if (busy) return
        if (name.isBlank() || description.isBlank()) { fail("Enter the organization name and description."); return }
        val missing = documents.indices.firstOrNull { documents[it] == null && uris[it] == null }
        if (missing != null) { fail("Attach ${labels[missing]}."); return }
        val user = FirebaseAuth.getInstance().currentUser ?: run { fail("Please sign in again."); return }
        busy = true
        progress.value = ApplicationProgress(true, "Checking your account…")
        val db = SupabaseDatabase.getInstance()
        db.collection("users").document(user.uid).get().addOnSuccessListener { profile ->
            if (!profile.exists() || profile.getString("role") !in listOf("student", "admin")) {
                fail("A registered Student account is required."); return@addOnSuccessListener
            }
            fun save() {
                if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) { fail("Your sign-in changed. Please try again."); return }
                progress.postValue(ApplicationProgress(true, "Saving application…"))
                val payload = mapOf("applicationId" to applicationId, "uid" to user.uid,
                    "fullName" to profile.getString("fullName").orEmpty(),
                    "username" to profile.getString("username").orEmpty(), "email" to user.email.orEmpty(),
                    "organizationName" to name, "organizationDescription" to description,
                    "organizationId" to "", "documents" to documents.filterNotNull(),
                    "documentUrl" to documents[0]?.get("url"), "documentPublicId" to documents[0]?.get("publicId"),
                    "role" to "admin", "status" to "pending", "rejectionReason" to "",
                    "updatedAt" to FieldValue.serverTimestamp())
                val ref = db.collection("adminApplications").document(applicationId)
                db.runTransaction { transaction ->
                    val previous = transaction.get(ref)
                    check(!previous.exists() || (previous.getString("uid") == user.uid && previous.getString("status") == "rejected")) {
                        "This application has already been submitted or reviewed. Return to your applications."
                    }
                    transaction.set(ref, payload + ("createdAt" to (previous.get("createdAt") ?: FieldValue.serverTimestamp())))
                }.addOnSuccessListener {
                    busy = false
                    progress.postValue(ApplicationProgress(message = "Organization application submitted for review.", complete = true))
                }.addOnFailureListener { fail(it.message ?: "Unable to save the application. Try again.") }
            }
            fun upload(index: Int) {
                if (index == 5) { save(); return }
                if (documents[index] != null) { upload(index + 1); return }
                val uri = uris[index] ?: run { fail("Attach ${labels[index]}."); return }
                progress.postValue(ApplicationProgress(true, "Uploading document ${index + 1} of 5…"))
                val publicId = "${applicationId}_${keys[index]}_${UUID.randomUUID()}"
                MediaManager.get().upload(uri).unsigned("kayara_upload")
                    .option("folder", "kayara/admin_applications").option("public_id", publicId)
                    .option("resource_type", "auto").callback(object : UploadCallback {
                        override fun onStart(requestId: String?) = Unit
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) = Unit
                        override fun onReschedule(requestId: String?, error: ErrorInfo?) = Unit
                        override fun onError(requestId: String?, error: ErrorInfo?) { fail("${labels[index]} could not upload. Check your connection and retry.") }
                        override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                            val url = resultData?.get("secure_url")?.toString()
                            if (url.isNullOrBlank()) { fail("The upload did not return a document URL. Try again."); return }
                            documents[index] = mapOf("key" to keys[index], "label" to labels[index],
                                "fileName" to names[index], "url" to url, "publicId" to publicId)
                            upload(index + 1)
                        }
                    }).dispatch()
            }
            upload(0)
        }.addOnFailureListener { fail(it.message ?: "Unable to load your profile. Try again.") }
    }

    private fun fail(message: String) {
        busy = false
        progress.postValue(ApplicationProgress(message = message, failed = true))
    }
}
