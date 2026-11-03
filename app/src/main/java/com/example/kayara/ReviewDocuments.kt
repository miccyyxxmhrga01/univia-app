package com.example.kayara

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ReviewDocumentsActivity : AppCompatActivity() {

    private lateinit var adapter: DocumentReviewAdapter
    private val documents = mutableListOf(
        DocumentReviewModel(
            "ITS_Constitution_Bylaws.docx", "IT Society", "Aug 27",
            "Constitution and by-laws document detailing membership rules, officer structure, and organizational goals for AY 2026–2027."
        ),
        DocumentReviewModel(
            "CSC_Financial_Statement_Q2.pdf", "College Student Council", "Aug 25",
            "Quarterly financial statement covering fund collections and disbursements for the second quarter."
        ),
        DocumentReviewModel(
            "RCY_Registration_Form.pdf", "Red Cross Youth", "Aug 24",
            "Official registration form for organization accreditation renewal, signed by the adviser and president."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_documents)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val rvDocuments = findViewById<RecyclerView>(R.id.rvDocuments)
        rvDocuments.layoutManager = LinearLayoutManager(this)

        adapter = DocumentReviewAdapter(
            documents,
            onApprove = { position ->
                documents[position].status = "approved"
                adapter.notifyItemChanged(position)
                // TODO: i-update ang status sa database, unya i-notify ang Admin
                Toast.makeText(this, "Document approved. Org Manager notified.", Toast.LENGTH_SHORT).show()
            },
            onReject = { position ->
                documents[position].status = "rejected"
                adapter.notifyItemChanged(position)
                // TODO: i-update ang status sa database, unya i-notify ang Admin
                Toast.makeText(this, "Document rejected. Org Manager notified.", Toast.LENGTH_SHORT).show()
            }
        )
        rvDocuments.adapter = adapter
    }
}