package com.example.kayara

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.google.firebase.auth.FirebaseAuth

/** Coordinator-only assignment of an existing account to an approved organization. */
class ManageAdminsActivity : AppCompatActivity() {
    private val db = SupabaseDatabase.getInstance()
    private lateinit var spinner: Spinner
    private lateinit var current: TextView
    private lateinit var result: TextView
    private lateinit var error: TextView
    private lateinit var search: EditText
    private var organizations = emptyList<DocumentSnapshot>()
    private var selected: DocumentSnapshot? = null
    private var candidate: DocumentSnapshot? = null
    private var generation = 0
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_org_access)
        UniviaInsets.apply(this)
        spinner = findViewById(R.id.organizationSpinner)
        current = findViewById(R.id.currentManager)
        result = findViewById(R.id.managerResult)
        error = findViewById(R.id.accessError)
        search = findViewById(R.id.managerSearch)
        search.doAfterTextChanged {
            candidate = null
            result.visibility = View.GONE
            findViewById<View>(R.id.btnAssignManager).visibility = View.GONE
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRetryAccess).setOnClickListener { loadOrganizations() }
        findViewById<View>(R.id.btnFindManager).setOnClickListener { findAccount() }
        search.setOnEditorActionListener { _, _, _ -> findAccount(); true }
        findViewById<View>(R.id.btnAssignManager).setOnClickListener { confirmChange(false) }
        findViewById<View>(R.id.btnRemoveManager).setOnClickListener { confirmChange(true) }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selected = organizations.getOrNull(position)
                generation++
                candidate = null
                result.visibility = View.GONE
                findViewById<View>(R.id.btnAssignManager).visibility = View.GONE
                showCurrent()
            }
        }
        AccountAccessGuard.attach(this) { it.coordinator }
        loadOrganizations()
    }

    private fun loadOrganizations(preferredId: String? = selected?.id) {
        generation++
        candidate = null
        result.visibility = View.GONE
        findViewById<View>(R.id.btnAssignManager).visibility = View.GONE
        setBusy(true)
        error.visibility = View.GONE
        findViewById<View>(R.id.btnRetryAccess).visibility = View.GONE
        db.collection("organizations").get().addOnSuccessListener(this) { snapshot ->
            organizations = snapshot.documents.sortedBy { it.getString("orgName").orEmpty().lowercase() }
            selected = null
            spinner.adapter = ArrayAdapter(this, R.layout.registration_spinner_item,
                organizations.map { it.getString("orgName") ?: it.id })
                .apply { setDropDownViewResource(R.layout.registration_spinner_item) }
            setBusy(false)
            if (organizations.isEmpty()) {
                current.setText(R.string.no_organizations_access)
                findViewById<View>(R.id.btnRemoveManager).visibility = View.GONE
            } else {
                spinner.setSelection(organizations.indexOfFirst { it.id == preferredId }.coerceAtLeast(0))
            }
        }.addOnFailureListener(this) {
            setBusy(false)
            showError(it.message ?: getString(R.string.access_load_failed))
            findViewById<View>(R.id.btnRetryAccess).visibility = View.VISIBLE
        }
    }

    private fun showCurrent() {
        val org = selected ?: return
        val uid = org.getString("adminUid").orEmpty()
        findViewById<View>(R.id.btnRemoveManager).visibility = if (uid.isBlank()) View.GONE else View.VISIBLE
        if (uid.isBlank()) { current.setText(R.string.no_manager); return }
        current.text = getString(R.string.current_manager, "Loading…")
        val request = generation
        db.collection("users").document(uid).get().addOnSuccessListener(this) {
            if (request == generation) current.text = getString(R.string.current_manager,
                it.getString("fullName") ?: it.getString("email") ?: uid)
        }.addOnFailureListener(this) {
            if (request == generation) current.text = getString(R.string.current_manager, uid)
        }
    }

    private fun findAccount() {
        if (busy) return
        error.visibility = View.GONE
        candidate = null
        result.visibility = View.GONE
        findViewById<View>(R.id.btnAssignManager).visibility = View.GONE
        if (selected == null) { showError(getString(R.string.no_organizations_access)); return }
        val value = search.text.toString().trim().lowercase()
        if (value.isBlank()) { showError("Enter the student's school ID or email."); return }
        val request = ++generation
        setBusy(true)
        db.collection("users").whereEqualTo(if ('@' in value) "email" else "studentId", value).get()
            .addOnSuccessListener(this) { snapshot ->
                setBusy(false)
                if (request != generation) return@addOnSuccessListener
                val matches = snapshot.documents.filter { it.getString("role") in listOf("student", "admin") }
                if (matches.size != 1) {
                    showError(if (matches.isEmpty()) "No registered account matches. Ask the student to register first."
                        else "More than one account matches this school ID. Search by the exact email instead.")
                    return@addOnSuccessListener
                }
                candidate = matches.single()
                result.text = listOfNotNull(candidate?.getString("fullName"), candidate?.getString("studentId"),
                    candidate?.getString("email")).joinToString("\n")
                result.visibility = View.VISIBLE
                val alreadyAssigned = selected?.getString("adminUid") == candidate?.id
                findViewById<TextView>(R.id.btnAssignManager).apply {
                    visibility = View.VISIBLE
                    isEnabled = !alreadyAssigned
                    setText(if (selected?.getString("adminUid").isNullOrBlank()) R.string.assign_manager else R.string.replace_manager)
                }
                if (alreadyAssigned) showError("This account already manages the selected organization.")
            }.addOnFailureListener(this) { setBusy(false); showError(it.message ?: getString(R.string.access_load_failed)) }
    }

    private fun confirmChange(remove: Boolean) {
        if (busy) return
        val org = selected ?: return
        val target = if (remove) null else candidate ?: return
        val manager = target?.getString("fullName") ?: target?.getString("email").orEmpty()
        val orgName = org.getString("orgName") ?: org.id
        AlertDialog.Builder(this)
            .setTitle(if (remove) R.string.remove_manager else R.string.assign_manager)
            .setMessage(if (remove) "Remove Org Manager access for $orgName? The account keeps its Student features."
                else "Give $manager Org Manager access to $orgName? Any previous manager will lose management access to this organization.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(if (remove) R.string.remove_manager else R.string.assign_manager) { _, _ ->
                saveAssignment(org, target)
            }.show()
    }

    private fun saveAssignment(org: DocumentSnapshot, target: DocumentSnapshot?) {
        val actor = FirebaseAuth.getInstance().currentUser ?: return
        setBusy(true)
        db.runTransaction { transaction ->
            val ref = db.collection("organizations").document(org.id)
            val latest = transaction.get(ref)
            check(latest.exists()) { "This organization no longer exists. Reload the list." }
            check(latest.getString("adminUid").orEmpty() == org.getString("adminUid").orEmpty()) {
                "The manager changed since you opened this screen. Reload before changing access."
            }
            if (target != null) {
                val profile = transaction.get(db.collection("users").document(target.id))
                check(profile.exists() && profile.getString("role") in listOf("student", "admin")) {
                    "This account is no longer eligible. Find the student again."
                }
            }
            transaction.update(ref, mapOf("adminUid" to (target?.id ?: ""),
                "managerAssignedBy" to actor.uid, "managerAssignedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()))
        }.addOnSuccessListener(this) {
            Toast.makeText(this, if (target == null) "Manager access removed." else "Manager access assigned.", Toast.LENGTH_LONG).show()
            candidate = null
            result.visibility = View.GONE
            findViewById<View>(R.id.btnAssignManager).visibility = View.GONE
            loadOrganizations(org.id)
        }.addOnFailureListener(this) {
            setBusy(false)
            showError(it.message ?: "Access could not be saved. Try again.")
            findViewById<View>(R.id.btnRetryAccess).visibility = View.VISIBLE
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        spinner.isEnabled = !value
        search.isEnabled = !value
        for (id in listOf(R.id.btnFindManager, R.id.btnAssignManager, R.id.btnRemoveManager, R.id.btnRetryAccess))
            findViewById<View>(id).isEnabled = !value
    }

    private fun showError(message: String) { error.text = message; error.visibility = View.VISIBLE }
}
