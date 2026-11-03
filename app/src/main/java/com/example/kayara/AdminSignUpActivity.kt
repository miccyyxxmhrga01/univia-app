package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.SupabaseDatabase
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

/** Existing signed-in users submit organization applications, without creating another account. */
class AdminSignUpActivity : AppCompatActivity() {
    private val model: OrganizationApplicationModel by viewModels()
    private lateinit var status: TextView
    private val documentButtons = mutableListOf<MaterialButton>()
    private var historyReady = false
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !model.busy) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val index = model.selectedSlot
            model.uris[index] = uri
            model.documents[index] = null
            model.names[index] = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else "Document ${index + 1}"
            } ?: "Document ${index + 1}"
            renderDocuments()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, StudentLoginActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_organization_application)
        UniviaInsets.apply(this)
        status = findViewById(R.id.tvError)
        if (model.editing) findViewById<TextView>(R.id.applicationFormTitle).text = "Revise organization application"
        val holder = findViewById<LinearLayout>(R.id.applicationDocumentButtons)
        for (index in 0..4) {
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                isAllCaps = false
                minHeight = (54 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    if (!model.busy) { model.selectedSlot = index; picker.launch(arrayOf("application/pdf", "image/*")) }
                }
            }
            documentButtons.add(button)
            holder.addView(button, LinearLayout.LayoutParams(-1, -2))
        }
        val back = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!model.busy) finish() }
        }
        onBackPressedDispatcher.addCallback(this, back)
        findViewById<View>(R.id.btnBack).setOnClickListener { back.handleOnBackPressed() }
        findViewById<View>(R.id.btnSignUp).setOnClickListener {
            if (historyReady) model.submit(findViewById<EditText>(R.id.etOrgName).text.toString().trim(),
                findViewById<EditText>(R.id.etDescription).text.toString().trim())
        }
        model.progress.observe(this) { progress ->
            findViewById<View>(R.id.btnSignUp).isEnabled = !progress.busy && historyReady
            findViewById<View>(R.id.etOrgName).isEnabled = !progress.busy
            findViewById<View>(R.id.etDescription).isEnabled = !progress.busy
            documentButtons.forEach { it.isEnabled = !progress.busy }
            status.text = progress.message
            status.setTextColor(getColor(if (progress.failed) R.color.error else R.color.text_primary))
            status.visibility = if (progress.message.isBlank()) View.GONE else View.VISIBLE
            if (progress.complete) { Toast.makeText(this, progress.message, Toast.LENGTH_LONG).show(); finish() }
        }
        renderDocuments()
        loadApplications()
    }

    private fun renderDocuments() {
        documentButtons.forEachIndexed { index, button ->
            val name = model.names[index].ifBlank { model.documents[index]?.get("fileName")?.toString().orEmpty() }
            button.text = OrganizationApplicationModel.labels[index] + if (name.isBlank()) "\nAttach document" else "\n$name"
        }
    }

    private fun loadApplications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        findViewById<View>(R.id.btnSignUp).isEnabled = false
        SupabaseDatabase.getInstance().collection("adminApplications").whereEqualTo("uid", uid).get()
            .addOnSuccessListener(this) { snapshot ->
                historyReady = true
                findViewById<View>(R.id.btnSignUp).isEnabled = !model.busy
                val history = findViewById<LinearLayout>(R.id.applicationHistory)
                history.removeAllViews()
                for (application in snapshot.documents) {
                    val state = application.getString("status").orEmpty()
                    val reason = application.getString("rejectionReason").orEmpty()
                    history.addView(TextView(this).apply {
                        text = "${application.getString("organizationName")}: $state" +
                            if (reason.isBlank()) "" else "\n$reason"
                        setTextColor(getColor(R.color.text_primary))
                        textSize = 14f
                        setPadding(0, 16, 0, 8)
                    }, LinearLayout.LayoutParams(-1, -2))
                    if (state == "rejected") {
                        history.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            isAllCaps = false
                            text = "Revise and resubmit"
                            setOnClickListener { if (!model.busy) edit(application) }
                        }, LinearLayout.LayoutParams(-1, -2))
                    }
                }
            }.addOnFailureListener(this) {
                status.text = "Unable to load applications. Tap here to retry."
                status.visibility = View.VISIBLE
                status.setOnClickListener { loadApplications() }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun edit(application: DocumentSnapshot) {
        model.applicationId = application.id
        model.editing = true
        model.uris.fill(null)
        model.names.fill("")
        model.documents.fill(null)
        val previous = application.get("documents") as? List<Map<String, Any?>> ?: emptyList()
        OrganizationApplicationModel.keys.forEachIndexed { index, key ->
            model.documents[index] = previous.firstOrNull { it["key"] == key }
        }
        findViewById<EditText>(R.id.etOrgName).setText(application.getString("organizationName"))
        findViewById<EditText>(R.id.etDescription).setText(application.getString("organizationDescription"))
        findViewById<TextView>(R.id.applicationFormTitle).text = "Revise organization application"
        renderDocuments()
    }
}
