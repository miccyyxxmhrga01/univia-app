package com.example.kayara

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateEventActivity : AppCompatActivity() {

    private val db =
        SupabaseDatabase.getInstance()

    private val auth =
        FirebaseAuth.getInstance()

    private lateinit var etTitle: EditText
    private lateinit var etDate: EditText
    private lateinit var etTime: EditText
    private lateinit var etVenue: EditText
    private lateinit var etDescription: EditText

    private lateinit var btnSaveEvent: MaterialButton

    private var organizationId = ""
    private var organizationName = ""


    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_create_event
        )


        // =====================================================
        // GET ORGANIZATION
        // =====================================================

        organizationId =
            intent.getStringExtra(
                "organizationId"
            )?.trim()
                ?: ""

        organizationName =
            intent.getStringExtra(
                "organizationName"
            )?.trim()
                ?: ""


        // =====================================================
        // FIND VIEWS
        // =====================================================

        etTitle =
            findViewById(
                R.id.etEventTitle
            )

        etDate =
            findViewById(
                R.id.etEventDate
            )

        etTime =
            findViewById(
                R.id.etEventTime
            )

        etVenue =
            findViewById(
                R.id.etEventVenue
            )

        etDescription =
            findViewById(
                R.id.etEventDescription
            )

        btnSaveEvent =
            findViewById(
                R.id.btnSaveEvent
            )


        // =====================================================
        // BACK BUTTON
        // btnBackCreateEvent is a FrameLayout/View
        // =====================================================

        findViewById<View>(
            R.id.btnBackCreateEvent
        ).setOnClickListener {

            finish()
        }


        // =====================================================
        // DATE
        // =====================================================

        etDate.setOnClickListener {

            showDatePicker()
        }


        // =====================================================
        // TIME
        // =====================================================

        etTime.setOnClickListener {

            showTimePicker()
        }


        // =====================================================
        // SAVE
        // =====================================================

        btnSaveEvent.setOnClickListener {

            saveEvent()
        }
    }


    // =========================================================
    // DATE PICKER
    // =========================================================

    private fun showDatePicker() {

        val calendar =
            Calendar.getInstance()

        val dialog =
            DatePickerDialog(
                this,
                { _, year, month, day ->

                    val selected =
                        Calendar.getInstance()

                    selected.set(
                        Calendar.YEAR,
                        year
                    )

                    selected.set(
                        Calendar.MONTH,
                        month
                    )

                    selected.set(
                        Calendar.DAY_OF_MONTH,
                        day
                    )

                    val format =
                        SimpleDateFormat(
                            "MMMM dd, yyyy",
                            Locale.getDefault()
                        )

                    etDate.setText(
                        format.format(
                            selected.time
                        )
                    )
                },

                calendar.get(
                    Calendar.YEAR
                ),

                calendar.get(
                    Calendar.MONTH
                ),

                calendar.get(
                    Calendar.DAY_OF_MONTH
                )
            )

        // Do not allow past dates
        dialog.datePicker.minDate =
            System.currentTimeMillis() - 1000

        dialog.show()
    }


    // =========================================================
    // TIME PICKER
    // =========================================================

    private fun showTimePicker() {

        val calendar =
            Calendar.getInstance()

        TimePickerDialog(
            this,

            { _, hour, minute ->

                val selected =
                    Calendar.getInstance()

                selected.set(
                    Calendar.HOUR_OF_DAY,
                    hour
                )

                selected.set(
                    Calendar.MINUTE,
                    minute
                )

                val format =
                    SimpleDateFormat(
                        "h:mm a",
                        Locale.getDefault()
                    )

                etTime.setText(
                    format.format(
                        selected.time
                    )
                )
            },

            calendar.get(
                Calendar.HOUR_OF_DAY
            ),

            calendar.get(
                Calendar.MINUTE
            ),

            false
        ).show()
    }


    // =========================================================
    // SAVE EVENT
    // =========================================================

    private fun saveEvent() {

        val title =
            etTitle.text
                .toString()
                .trim()

        val date =
            etDate.text
                .toString()
                .trim()

        val time =
            etTime.text
                .toString()
                .trim()

        val venue =
            etVenue.text
                .toString()
                .trim()

        val description =
            etDescription.text
                .toString()
                .trim()


        // =====================================================
        // VALIDATION
        // =====================================================

        if (title.isBlank()) {

            etTitle.error =
                "Event title is required."

            etTitle.requestFocus()

            return
        }


        if (date.isBlank()) {

            Toast.makeText(
                this,
                "Please select an event date.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        if (time.isBlank()) {

            Toast.makeText(
                this,
                "Please select an event time.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        if (venue.isBlank()) {

            etVenue.error =
                "Venue is required."

            etVenue.requestFocus()

            return
        }


        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization information is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val uid =
            auth.currentUser?.uid

        if (uid == null) {

            Toast.makeText(
                this,
                "User not signed in.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        // =====================================================
        // DISABLE WHILE SAVING
        // =====================================================

        btnSaveEvent.isEnabled =
            false

        btnSaveEvent.text =
            "Creating Event..."


        // =====================================================
        // CREATE FIRESTORE DOCUMENT
        // =====================================================

        val eventRef =
            db.collection(
                "events"
            ).document()


        val data =
            hashMapOf<String, Any>(

                "eventId" to
                        eventRef.id,

                "organizationId" to
                        organizationId,

                "organizationName" to
                        organizationName,

                "title" to
                        title,

                "date" to
                        date,

                "time" to
                        time,

                "venue" to
                        venue,

                "description" to
                        description,

                "createdBy" to
                        uid,

                "status" to
                        "upcoming",

                "createdAt" to
                        System.currentTimeMillis()
            )


        eventRef
            .set(data)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Event created successfully.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
            .addOnFailureListener { error ->

                btnSaveEvent.isEnabled =
                    true

                btnSaveEvent.text =
                    "Create Event"

                Toast.makeText(
                    this,
                    "Unable to create event: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}