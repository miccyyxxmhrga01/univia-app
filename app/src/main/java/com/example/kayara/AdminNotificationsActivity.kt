package com.example.kayara

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AdminNotificationsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val notifications = listOf(
            NotificationModel(
                "Application Approved",
                "Your organization Red Cross Youth has been approved by the Coordinator",
                "2 hours ago", "signup"
            ),
            NotificationModel(
                "Document Approved",
                "Your submitted RCY_Accomplishment_Report.pdf was approved",
                "1 day ago", "document"
            ),
            NotificationModel(
                "New Announcement",
                "Coordinator posted: General Assembly – September 5, 1:00 PM",
                "2 days ago", "info", isRead = true
            ),
            NotificationModel(
                "New Membership Request",
                "3 new students applied to join Red Cross Youth",
                "3 days ago", "signup", isRead = true
            )
        )

        val rvNotifications = findViewById<RecyclerView>(R.id.rvNotifications)
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = NotificationAdapter(notifications)
    }
}