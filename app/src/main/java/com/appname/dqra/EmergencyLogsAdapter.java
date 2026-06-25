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
import com.appname.dqra.repository.EmergencyRepository;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;

public class EmergencyLogsAdapter extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EmergencyLocationAdapter adapter;
    private List<EmergencyLocation> emergencyList;

    private EmergencyRepository emergencyRepository;

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

        emergencyRepository = new EmergencyRepository();

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
        emergencyRepository.fetchCurrentUserMunicipality(new EmergencyRepository.Callback<String>() {
            @Override
            public void onSuccess(String municipality) {
                Log.d("AdminActivity", "User Municipality: " + municipality);
                fetchEmergencyLogs(municipality);
            }

            @Override
            public void onError(Exception exception) {
                Log.e("AdminActivity", "Error fetching user municipality", exception);
                Toast.makeText(EmergencyLogsAdapter.this,
                        exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Fetch emergency logs with optional status filter
     */
    private void fetchEmergencyLogs(String municipality) {
        Log.d("AdminActivity", "Fetching reports for Municipality: " + municipality +
                ", Filter: " + (statusFilter != null ? statusFilter : "all"));

        emergencyRepository.fetchEmergencyLogs(municipality, statusFilter,
                new EmergencyRepository.Callback<List<EmergencyLocation>>() {
                    @Override
                    public void onSuccess(List<EmergencyLocation> emergencies) {
                        emergencyList.clear();
                        emergencyList.addAll(emergencies);

                        Log.d("AdminActivity", "Total Reports Found: " + emergencyList.size());

                        if (emergencyList.isEmpty()) {
                            String message = statusFilter != null
                                    ? "No " + statusFilter + " emergencies found"
                                    : "No emergency reports found";
                            Toast.makeText(EmergencyLogsAdapter.this, message, Toast.LENGTH_SHORT).show();
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Log.e("AdminActivity", "Error fetching emergency reports", exception);
                        Toast.makeText(EmergencyLogsAdapter.this,
                                "Failed to load emergency reports!", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
