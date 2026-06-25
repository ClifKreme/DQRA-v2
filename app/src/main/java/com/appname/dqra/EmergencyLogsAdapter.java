package com.appname.dqra;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.appname.dqra.model.EmergencyLocation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;

public class EmergencyLogsAdapter extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EmergencyLocationAdapter adapter;
    private List<EmergencyLocation> emergencyList;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Status filter
    private String statusFilter = null; // null = show all
    private TextView tvLogsTitle; // Title to show what's being displayed

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergency_logs);

        // Get status filter from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("STATUS_FILTER")) {
            statusFilter = intent.getStringExtra("STATUS_FILTER");
        }

        // Initialize title TextView
        tvLogsTitle = findViewById(R.id.tvLogsTitle);
        updateTitle();

        // Initialize Back Button as ImageButton
        ImageButton btnBack = findViewById(R.id.btnBackToDashboard);
        btnBack.setOnClickListener(v -> {
            Intent backIntent = new Intent(EmergencyLogsAdapter.this, AdminDashboard.class);
            startActivity(backIntent);
            finish();
        });

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerViewEmergencyLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        emergencyList = new ArrayList<>();
        adapter = new EmergencyLocationAdapter(this, emergencyList);
        recyclerView.setAdapter(adapter);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        fetchUserMunicipality();
    }

    /**
     * Update title based on filter
     */
    private void updateTitle() {
        if (tvLogsTitle != null) {
            String title;
            switch (statusFilter != null ? statusFilter : "all") {
                case "pending":
                    title = "📋 Pending Emergencies";
                    break;
                case "confirmed":
                    title = "🚨 Active Emergencies";
                    break;
                case "resolved":
                    title = "✅ Resolved Emergencies";
                    break;
                default:
                    title = "📋 All Emergency Logs";
                    break;
            }
            tvLogsTitle.setText(title);
        }
    }

    /**
     * Fetch the municipality associated with the current user (admin or rescuer)
     */
    private void fetchUserMunicipality() {
        String userId = auth.getCurrentUser().getUid();
        Log.d("AdminActivity", "Fetching data for User ID: " + userId);

        // First, check in the "admins_with_stations" collection
        db.collection("admins_with_stations").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String municipality = documentSnapshot.getString("municipality");
                        Log.d("AdminActivity", "Admin Municipality: " + municipality);

                        if (municipality != null) {
                            fetchEmergencyLogs(municipality);
                        } else {
                            Toast.makeText(this, "Error: Municipality not set for admin!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // If not an admin, check if user is a rescuer
                        fetchRescuerMunicipality(userId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminActivity", "Error fetching admin data", e);
                    Toast.makeText(this, "Failed to load admin details!", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Fallback method to check if user is a rescuer and fetch their municipality
     */
    private void fetchRescuerMunicipality(String userId) {
        db.collection("rescuers").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String municipality = documentSnapshot.getString("municipality");
                        Log.d("AdminActivity", "Rescuer Municipality: " + municipality);

                        if (municipality != null) {
                            fetchEmergencyLogs(municipality);
                        } else {
                            Toast.makeText(this, "Error: Municipality not set for rescuer!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // User not found in either collection
                        Toast.makeText(this, "Error: User not found in any collection!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminActivity", "Error fetching rescuer data", e);
                    Toast.makeText(this, "Failed to load rescuer details!", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Fetch emergency logs with optional status filter
     */
    private void fetchEmergencyLogs(String municipality) {
        Log.d("AdminActivity", "Fetching reports for Municipality: " + municipality +
                ", Filter: " + (statusFilter != null ? statusFilter : "all"));

        // Build query based on filter
        com.google.firebase.firestore.Query query = db.collection("emergency_locations")
                .whereEqualTo("municipality", municipality);

        // Add status filter if specified
        if (statusFilter != null) {
            query = query.whereEqualTo("status", statusFilter);
        }

        // Execute query
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    emergencyList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        EmergencyLocation emergency = document.toObject(EmergencyLocation.class);
                        emergencyList.add(emergency);
                    }

                    Log.d("AdminActivity", "Total Reports Found: " + emergencyList.size());

                    // Show message if no results
                    if (emergencyList.isEmpty()) {
                        String message;
                        if (statusFilter != null) {
                            message = "No " + statusFilter + " emergencies found";
                        } else {
                            message = "No emergency reports found";
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e("AdminActivity", "Error fetching emergency reports", e);
                    Toast.makeText(this, "Failed to load emergency reports!", Toast.LENGTH_SHORT).show();
                });
    }
}