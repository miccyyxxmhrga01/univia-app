package com.example.kayara

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.Query

class SuperAdminApplicationsFragment : Fragment() {

    private lateinit var db: SupabaseDatabase

    private lateinit var recyclerApplications: RecyclerView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvEmpty: View

    private lateinit var adapter: SuperAdminApplicationAdapter

    private val applications =
        mutableListOf<AdminApplicationModel>()

    private var applicationsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = SupabaseDatabase.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_super_admin_applications,
            container,
            false
        )

        recyclerApplications =
            view.findViewById(R.id.recyclerApplications)

        tvPendingCount =
            view.findViewById(R.id.tvPendingCount)

        tvEmpty =
            view.findViewById(R.id.tvEmpty)

        setupRecyclerView()

        loadPendingApplications()

        return view
    }

    private fun setupRecyclerView() {

        adapter = SuperAdminApplicationAdapter(
            applications
        ) { application ->

            openApplication(application)
        }

        recyclerApplications.layoutManager =
            LinearLayoutManager(requireContext())

        recyclerApplications.adapter =
            adapter
    }

    private fun loadPendingApplications() {

        applicationsListener?.remove()

        applicationsListener =
            db.collection("adminApplications")
                .whereEqualTo(
                    "status",
                    "pending"
                )
                .orderBy(
                    "createdAt",
                    Query.Direction.DESCENDING
                )
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Failed to load applications: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addSnapshotListener
                    }

                    applications.clear()

                    snapshot?.documents?.forEach { document ->

                        val application =
                            document.toObject(
                                AdminApplicationModel::class.java
                            )

                        if (application != null) {

                            applications.add(
                                application.copy(
                                    applicationId =
                                        if (application.applicationId.isBlank())
                                            document.id
                                        else
                                            application.applicationId
                                )
                            )
                        }
                    }

                    adapter.updateApplications(
                        applications
                    )

                    tvPendingCount.text =
                        applications.size.toString()

                    if (applications.isEmpty()) {

                        tvEmpty.visibility =
                            View.VISIBLE

                        recyclerApplications.visibility =
                            View.GONE

                    } else {

                        tvEmpty.visibility =
                            View.GONE

                        recyclerApplications.visibility =
                            View.VISIBLE
                    }
                }
    }

    private fun openApplication(
        application: AdminApplicationModel
    ) {

        if (application.applicationId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Application ID not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                SuperAdminApplicationReviewFragment.newInstance(
                    application.applicationId
                )
            )
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {

        applicationsListener?.remove()
        applicationsListener = null

        super.onDestroyView()
    }
}