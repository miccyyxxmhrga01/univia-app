package com.example.kayara

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class EmailSenderActivity : AppCompatActivity() {
    private val model: EmailSenderModel by viewModels()
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_sender)
        UniviaInsets.apply(this)
        val email = findViewById<EditText>(R.id.senderEmail)
        password = findViewById(R.id.senderPassword)
        val status = findViewById<TextView>(R.id.senderStatus)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.connectSender).setOnClickListener {
            val address = email.text.toString().trim().lowercase()
            val secret = password.text.toString().filterNot { it.isWhitespace() }
            if (!Patterns.EMAIL_ADDRESS.matcher(address).matches() || !secret.matches(Regex("^[A-Za-z0-9]{16}$"))) {
                status.setText(R.string.sender_invalid)
                return@setOnClickListener
            }
            model.save(address, secret)
        }
        model.state.observe(this) {
            if (!email.hasFocus()) email.setText(it.email)
            email.isEnabled = !it.busy
            password.isEnabled = !it.busy
            findViewById<View>(R.id.connectSender).isEnabled = !it.busy
            status.setText(it.message)
            if (it.saved) password.text.clear()
        }
        AccountAccessGuard.attach(this) { it.coordinator }
        model.load()
    }

    override fun onStop() {
        if (::password.isInitialized) password.text.clear()
        super.onStop()
    }
}
