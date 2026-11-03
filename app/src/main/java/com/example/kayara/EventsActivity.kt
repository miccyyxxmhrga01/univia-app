package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration

class EventsActivity : AppCompatActivity() {

    private val db =
        SupabaseDatabase.getInstance()

    private val auth =
        FirebaseAuth.getInstance()

    private lateinit var rvEvents: RecyclerView

    private lateinit var adapter: EventAdapter

    private lateinit var tvEmpty: TextView

    private lateinit var fabAddEvent: FloatingActionButton

    private var organizationId = ""

    private var organizationName = ""

    private var eventsListener:
            ListenerRegistration? = null

    private var currentRole = ""

    private var currentUserOrganizationId = ""

    private var canManageEvents = false


    // =========================================================
    // CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_events
        )

        organizationId =
            intent.getStringExtra(
                "organizationId"
            )?.trim()
                ?: ""

        organizationName =
            intent.getStringExtra(
                "organizationName"
            )?.trim()
                ?: "Organization"


        // =====================================================
        // TITLE
        // =====================================================

        findViewById<TextView>(
            R.id.tvEventsTitle
        ).text =
            "$organizationName Events"


        // =====================================================
        // BACK
        // =====================================================

        findViewById<View>(
            R.id.btnBackEvents
        ).setOnClickListener {
            finish()
        }

        // =====================================================
        // VIEWS
        // =====================================================

        rvEvents =
            findViewById(
                R.id.rvEvents
            )

        tvEmpty =
            findViewById(
                R.id.tvEmptyEvents
            )

        fabAddEvent =
            findViewById(
                R.id.fabAddEvent
            )


        // hide first until permission is verified
        fabAddEvent.visibility =
            View.GONE


        // =====================================================
        // RECYCLER
        // =====================================================

        adapter =
            EventAdapter(
                events = mutableListOf(),

                onLongClick = { event ->

                    handleEventLongClick(
                        event
                    )
                }
            )

        rvEvents.layoutManager =
            LinearLayoutManager(this)

        rvEvents.adapter =
            adapter


        // =====================================================
        // LOAD
        // =====================================================

        checkManagePermission()

        listenToEvents()
    }


    // =========================================================
    // CHECK ROLE / PERMISSION
    // =========================================================

    private fun checkManagePermission() {

        val uid =
            auth.currentUser?.uid

        if (uid == null) {

            canManageEvents =
                false

            fabAddEvent.visibility =
                View.GONE

            return
        }

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                currentRole =
                    document.getString("role")
                        ?.trim()
                        ?.lowercase()
                        ?: ""

                currentUserOrganizationId =
                    document.getString(
                        "organizationId"
                    )
                        ?.trim()
                        ?: ""


                // =================================================
                // ADMIN = own organization only
                // SUPER ADMIN = all organizations
                // =================================================

                canManageEvents =
                    currentRole == "superadmin" ||
                            currentRole == "super_admin" ||
                            (
                                    currentRole == "admin" &&
                                            currentUserOrganizationId.isNotBlank() &&
                                            currentUserOrganizationId ==
                                            organizationId
                                    )


                // =================================================
                // CREATE BUTTON
                //
                // Only ADMIN creates events.
                // Super Admin can view/delete.
                // =================================================

                val canCreate =
                    currentRole == "admin" &&
                            currentUserOrganizationId.isNotBlank() &&
                            currentUserOrganizationId ==
                            organizationId


                fabAddEvent.visibility =
                    if (canCreate) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }


                if (canCreate) {

                    fabAddEvent.setOnClickListener {

                        openCreateEvent()
                    }
                }
            }
            .addOnFailureListener {

                canManageEvents =
                    false

                fabAddEvent.visibility =
                    View.GONE
            }
    }


    // =========================================================
    // OPEN CREATE EVENT
    // =========================================================

    private fun openCreateEvent() {

        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val intent =
            Intent(
                this,
                CreateEventActivity::class.java
            )

        intent.putExtra(
            "organizationId",
            organizationId
        )

        intent.putExtra(
            "organizationName",
            organizationName
        )

        startActivity(intent)
    }


    // =========================================================
    // LISTEN TO EVENTS
    // =========================================================

    private fun listenToEvents() {

        if (organizationId.isBlank()) {

            tvEmpty.visibility =
                View.VISIBLE

            tvEmpty.text =
                "Organization not found."

            return
        }

        eventsListener?.remove()


        eventsListener =
            db.collection("events")
                .whereEqualTo(
                    "organizationId",
                    organizationId
                )
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {

                        Toast.makeText(
                            this,
                            "Unable to load events.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }


                    if (snapshot == null) {
                        return@addSnapshotListener
                    }


                    val events =
                        snapshot.documents
                            .map { document ->

                                EventModel(

                                    eventId =
                                        document.id,

                                    organizationId =
                                        document.getString(
                                            "organizationId"
                                        ) ?: "",

                                    organizationName =
                                        document.getString(
                                            "organizationName"
                                        ) ?: "",

                                    title =
                                        document.getString(
                                            "title"
                                        ) ?: "",

                                    description =
                                        document.getString(
                                            "description"
                                        ) ?: "",

                                    date =
                                        document.getString(
                                            "date"
                                        ) ?: "",

                                    time =
                                        document.getString(
                                            "time"
                                        ) ?: "",

                                    venue =
                                        document.getString(
                                            "venue"
                                        ) ?: "",

                                    createdBy =
                                        document.getString(
                                            "createdBy"
                                        ) ?: "",

                                    status =
                                        document.getString(
                                            "status"
                                        ) ?: "upcoming",

                                    createdAt =
                                        document.getLong(
                                            "createdAt"
                                        ) ?: 0L
                                )
                            }
                            .sortedByDescending {
                                it.createdAt
                            }


                    adapter.updateEvents(
                        events
                    )


                    tvEmpty.visibility =
                        if (events.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }


                    rvEvents.visibility =
                        if (events.isEmpty()) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }
                }
    }


    // =========================================================
    // LONG PRESS
    // =========================================================

    private fun handleEventLongClick(
        event: EventModel
    ) {

        if (!canManageEvents) {

            // Student / unauthorized account:
            // no delete option.
            return
        }

        confirmDeleteEvent(
            event
        )
    }


    // =========================================================
    // CONFIRM DELETE
    // =========================================================

    private fun confirmDeleteEvent(
        event: EventModel
    ) {

        AlertDialog.Builder(this)
            .setTitle(
                "Delete Event"
            )
            .setMessage(
                "Are you sure you want to delete \"${event.title}\"?"
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Delete"
            ) { _, _ ->

                deleteEvent(
                    event
                )
            }
            .show()
    }


    // =========================================================
    // DELETE EVENT
    // =========================================================

    private fun deleteEvent(
        event: EventModel
    ) {

        if (!canManageEvents) {

            Toast.makeText(
                this,
                "You do not have permission to delete this event.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        if (event.eventId.isBlank()) {

            Toast.makeText(
                this,
                "Event ID not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        db.collection("events")
            .document(
                event.eventId
            )
            .delete()
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Event deleted.",
                    Toast.LENGTH_SHORT
                ).show()

                // No need to manually remove from adapter.
                // Firestore listener updates automatically.
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Unable to delete event: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroy() {

        eventsListener?.remove()

        eventsListener =
            null

        super.onDestroy()
    }
}