package com.example.kayara

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.kayara.data.SupabaseDatabase

data class EmailSenderState(val busy: Boolean = false, val configured: Boolean = false,
    val email: String = "organizationapp1102@gmail.com", val message: Int = R.string.sender_not_ready, val saved: Boolean = false)

/** The password is an operation argument only, never saved in model or view state. */
class EmailSenderModel : ViewModel() {
    val state = MutableLiveData(EmailSenderState())
    private val db = SupabaseDatabase.getInstance()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        db.emailSenderStatus().addOnSuccessListener {
            state.value = EmailSenderState(configured = it.configured,
                email = it.email.ifBlank { "organizationapp1102@gmail.com" },
                message = if (it.configured) R.string.sender_ready else R.string.sender_not_ready)
        }.addOnFailureListener { state.value = EmailSenderState(message = R.string.sender_failed) }
    }

    fun save(email: String, password: String) {
        if (state.value?.busy == true) return
        state.value = EmailSenderState(true, email = email, message = R.string.sender_connecting)
        db.configureEmailSender(email, password).addOnSuccessListener {
            state.value = EmailSenderState(configured = it.configured, email = it.email,
                message = R.string.sender_ready, saved = true)
        }.addOnFailureListener {
            state.value = EmailSenderState(email = email, message = R.string.sender_failed)
        }
    }
}
