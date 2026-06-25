package com.appname.dqra;

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
import com.appname.dqra.repository.EmergencyRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmergencyLocationAdapter extends RecyclerView.Adapter<EmergencyLocationAdapter.ViewHolder> {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String STATUS_REJECTED = "rejected";

    private final Context context;
    private final List<EmergencyLocation> emergencyList;
    private final EmergencyRepository emergencyRepository;

    public EmergencyLocationAdapter(Context context, List<EmergencyLocation> emergencyList) {
        this.context = context;
        this.emergencyList = emergencyList;
        this.emergencyRepository = new EmergencyRepository();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.emergency_location_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyLocation emergency = emergencyList.get(position);

        holder.tvMunicipality.setText("Municipality: " + emergency.getMunicipality());
        holder.tvReportedBy.setText("Reported by: " + emergency.getReportedBy());

        String formattedDate = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                .format(new Date(emergency.getTimestamp()));
        holder.tvTimestamp.setText("Date: " + formattedDate);

        String status = emergency.getStatus();
        if (status != null) {
            holder.tvMunicipality.setText("Municipality: " + emergency.getMunicipality()
                    + "  |  " + getStatusBadge(status));
        }

        fetchPhoneNumber(emergency.getReportedBy(), holder.tvPhoneNumber, holder.btnCall);

        holder.btnViewMap.setOnClickListener(v -> {
            String uri = "geo:" + emergency.getLatitude() + "," + emergency.getLongitude()
                    + "?q=" + emergency.getLatitude() + "," + emergency.getLongitude();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");
            context.startActivity(intent);
        });

        if (STATUS_CONFIRMED.equals(status)) {
            holder.btnResolve.setVisibility(View.VISIBLE);
            holder.btnResolve.setOnClickListener(v -> showResolveConfirmationDialog(emergency, position));
        } else {
            holder.btnResolve.setVisibility(View.GONE);
        }
    }

    private String getStatusBadge(String status) {
        switch (status) {
            case STATUS_PENDING:
                return "PENDING";
            case STATUS_CONFIRMED:
                return "ACTIVE";
            case STATUS_RESOLVED:
                return "RESOLVED";
            case STATUS_REJECTED:
                return "REJECTED";
            default:
                return status.toUpperCase();
        }
    }

    private void showResolveConfirmationDialog(EmergencyLocation emergency, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Resolve Emergency")
                .setMessage("Mark this emergency as resolved?\n\nReported by: " + emergency.getReportedBy()
                        + "\nMunicipality: " + emergency.getMunicipality())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Resolve", (dialog, which) -> resolveEmergency(emergency, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resolveEmergency(EmergencyLocation emergency, int position) {
        emergencyRepository.resolveEmergency(emergency, new EmergencyRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                Toast.makeText(context, "Emergency marked as resolved", Toast.LENGTH_SHORT).show();
                emergency.setStatus(STATUS_RESOLVED);
                notifyItemChanged(position);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(context, "Failed to resolve: " + exception.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchPhoneNumber(String reportedBy, TextView textView, Button btnCall) {
        emergencyRepository.fetchPhoneNumber(reportedBy, new EmergencyRepository.Callback<String>() {
            @Override
            public void onSuccess(String phoneNumber) {
                textView.setText("Phone: " + (phoneNumber != null ? phoneNumber : "Not Available"));

                if (phoneNumber != null) {
                    btnCall.setVisibility(View.VISIBLE);
                    btnCall.setOnClickListener(v -> {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + phoneNumber));
                        context.startActivity(callIntent);
                    });
                } else {
                    btnCall.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(Exception exception) {
                textView.setText("Phone: Not Available");
                btnCall.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emergencyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvMunicipality, tvReportedBy, tvTimestamp, tvPhoneNumber;
        public Button btnViewMap, btnCall, btnResolve;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMunicipality = itemView.findViewById(R.id.tvMunicipality);
            tvReportedBy = itemView.findViewById(R.id.tvReportedBy);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvPhoneNumber = itemView.findViewById(R.id.tvPhoneNumber);
            btnViewMap = itemView.findViewById(R.id.btnViewMap);
            btnCall = itemView.findViewById(R.id.btnCall);
            btnResolve = itemView.findViewById(R.id.btnResolve);
        }
    }
}
