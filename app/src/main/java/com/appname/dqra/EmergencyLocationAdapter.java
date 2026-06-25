package com.appname.dqra;

// Import necessary Android and Java libraries
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.EmergencyLocation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter class for displaying emergency location items in a RecyclerView
 * Handles binding data to views and setting up click listeners
 */
public class EmergencyLocationAdapter extends RecyclerView.Adapter<EmergencyLocationAdapter.ViewHolder> {

    // ✅ NEW: Status constants to avoid typos
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String STATUS_REJECTED = "rejected";

    // Context for accessing resources and starting activities
    private Context context;
    // List of emergency location data to display
    private List<EmergencyLocation> emergencyList;
    // Firebase Firestore database instance for retrieving additional data
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth auth = FirebaseAuth.getInstance();

    /**
     * Constructor to initialize the adapter with context and data list
     * @param context Application context
     * @param emergencyList List of emergency locations to display
     */
    public EmergencyLocationAdapter(Context context, List<EmergencyLocation> emergencyList) {
        this.context = context;
        this.emergencyList = emergencyList;
    }

    /**
     * Creates a new ViewHolder when needed by inflating the item layout
     * @param parent Parent ViewGroup
     * @param viewType View type (not used in this implementation)
     * @return New ViewHolder instance
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.emergency_location_item, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Binds data to the ViewHolder at the specified position
     * Sets text for TextViews and click listeners for buttons
     * @param holder ViewHolder to update
     * @param position Position of the item in the data list
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyLocation emergency = emergencyList.get(position);

        holder.tvMunicipality.setText("Municipality: " + emergency.getMunicipality());
        holder.tvReportedBy.setText("Reported by: " + emergency.getReportedBy());

        String formattedDate = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(new Date(emergency.getTimestamp()));
        holder.tvTimestamp.setText("Date: " + formattedDate);

        // Add status badge
        String status = emergency.getStatus();
        if (status != null) {
            String statusBadge;
            switch (status) {
                case STATUS_PENDING: // ✅ FIXED: Use constant
                    statusBadge = "⏳ PENDING";
                    break;
                case STATUS_CONFIRMED: // ✅ FIXED: Use constant
                    statusBadge = "🚨 ACTIVE";
                    break;
                case STATUS_RESOLVED: // ✅ FIXED: Use constant
                    statusBadge = "✅ RESOLVED";
                    break;
                case STATUS_REJECTED: // ✅ FIXED: Use constant
                    statusBadge = "❌ REJECTED";
                    break;
                default:
                    statusBadge = "⚪ " + status.toUpperCase();
                    break;
            }
            holder.tvMunicipality.setText("Municipality: " + emergency.getMunicipality() +
                    "  |  " + statusBadge);
        }

        fetchPhoneNumber(emergency.getReportedBy(), holder.tvPhoneNumber, holder.btnCall);

        // View Map button
        holder.btnViewMap.setOnClickListener(v -> {
            String uri = "geo:" + emergency.getLatitude() + "," + emergency.getLongitude() +
                    "?q=" + emergency.getLatitude() + "," + emergency.getLongitude();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            context.startActivity(intent);
        });

        // NEW: Resolve button visibility and click listener
        if (STATUS_CONFIRMED.equals(status)) { // ✅ FIXED: Use constant
            // Show resolve button only for active/confirmed emergencies
            holder.btnResolve.setVisibility(View.VISIBLE);
            holder.btnResolve.setOnClickListener(v -> {
                showResolveConfirmationDialog(emergency, position);
            });
        } else {
            // Hide resolve button for pending, rejected, or already resolved
            holder.btnResolve.setVisibility(View.GONE);
        }
    }

    /**
     * NEW: Show confirmation dialog before resolving emergency
     */
    private void showResolveConfirmationDialog(EmergencyLocation emergency, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Resolve Emergency")
                .setMessage("Mark this emergency as resolved?\n\nReported by: " + emergency.getReportedBy() +
                        "\nMunicipality: " + emergency.getMunicipality())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Resolve", (dialog, which) -> {
                    resolveEmergency(emergency, position);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * NEW: Resolve the emergency in Firestore
     */
    private void resolveEmergency(EmergencyLocation emergency, int position) {
        // ✅ FIXED: Use document ID directly instead of expensive query
        String docId = emergency.getId();
        
        if (docId == null || docId.isEmpty()) {
            // Fallback to query if ID not available (backward compatibility)
            resolveEmergencyByQuery(emergency, position);
            return;
        }

        // Update the document directly
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", STATUS_RESOLVED); // ✅ FIXED: Use constant
        updates.put("resolvedAt", FieldValue.serverTimestamp());
        updates.put("resolvedBy", auth.getCurrentUser() != null ?
                auth.getCurrentUser().getUid() : "unknown");

        db.collection("emergency_locations")
                .document(docId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "✓ Emergency marked as resolved",
                            Toast.LENGTH_SHORT).show();

                    // Update local list
                    emergency.setStatus(STATUS_RESOLVED); // ✅ FIXED: Use constant
                    notifyItemChanged(position);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Failed to resolve: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Fallback method using query (for backward compatibility)
     */
    private void resolveEmergencyByQuery(EmergencyLocation emergency, int position) {
        // We need the document ID - we'll search for it
        db.collection("emergency_locations")
                .whereEqualTo("reportedBy", emergency.getReportedBy())
                .whereEqualTo("timestamp", emergency.getTimestamp())
                .whereEqualTo("municipality", emergency.getMunicipality())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String docId = queryDocumentSnapshots.getDocuments().get(0).getId();

                        // Update the document
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", STATUS_RESOLVED); // ✅ FIXED: Use constant
                        updates.put("resolvedAt", FieldValue.serverTimestamp());
                        updates.put("resolvedBy", auth.getCurrentUser() != null ?
                                auth.getCurrentUser().getUid() : "unknown");

                        db.collection("emergency_locations")
                                .document(docId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(context, "✓ Emergency marked as resolved",
                                            Toast.LENGTH_SHORT).show();

                                    // Update local list
                                    emergency.setStatus(STATUS_RESOLVED); // ✅ FIXED: Use constant
                                    notifyItemChanged(position);
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(context, "Failed to resolve: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Toast.makeText(context, "Emergency not found in database",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Error finding emergency: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Fetches the phone number for a reporter from Firestore database
     * Updates the phone number TextView and call button based on results
     * @param reportedBy Name of the person who reported the emergency
     * @param textView TextView to display the phone number
     * @param btnCall Button to initiate a call
     */
    private void fetchPhoneNumber(String reportedBy, TextView textView, Button btnCall) {
        // Query Firestore for documents where reportedBy matches the given name
        db.collection("emergency_locations")
                .whereEqualTo("reportedBy", reportedBy)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        QuerySnapshot snapshot = task.getResult();
                        if (!snapshot.isEmpty()) {
                            // Extract phone number from the first matching document
                            String phoneNumber = snapshot.getDocuments().get(0).getString("phoneNumber");
                            // Set the phone number text
                            textView.setText("Phone: " + (phoneNumber != null ? phoneNumber : "Not Available"));

                            if (phoneNumber != null) {
                                // Show call button and set its click listener if phone number exists
                                btnCall.setVisibility(View.VISIBLE);
                                btnCall.setOnClickListener(v -> {
                                    // Create intent to dial the phone number
                                    Intent callIntent = new Intent(Intent.ACTION_DIAL);
                                    callIntent.setData(Uri.parse("tel:" + phoneNumber));
                                    context.startActivity(callIntent);
                                });
                            } else {
                                // Hide call button if no phone number
                                btnCall.setVisibility(View.GONE);
                            }
                        } else {
                            // No matching documents found
                            textView.setText("Phone: Not Available");
                            btnCall.setVisibility(View.GONE);
                        }
                    }
                });
    }

    /**
     * Returns the total number of items in the data set
     * @return Size of the emergency list
     */
    @Override
    public int getItemCount() {
        return emergencyList.size();
    }

    /**
     * ViewHolder class that holds references to the views for each item
     * Caches these references to avoid repeated findViewById calls
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // UI elements for displaying emergency location information
        public TextView tvMunicipality, tvReportedBy, tvTimestamp, tvPhoneNumber;
        public Button btnViewMap, btnCall, btnResolve; // NEW: Added btnResolve

        /**
         * Constructor to initialize view references
         * @param itemView The item view
         */
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find and store references to all views in the item layout
            tvMunicipality = itemView.findViewById(R.id.tvMunicipality);
            tvReportedBy = itemView.findViewById(R.id.tvReportedBy);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvPhoneNumber = itemView.findViewById(R.id.tvPhoneNumber);
            btnViewMap = itemView.findViewById(R.id.btnViewMap);
            btnCall = itemView.findViewById(R.id.btnCall);
            btnResolve = itemView.findViewById(R.id.btnResolve); // NEW: Initialize resolve button
        }
    }
}