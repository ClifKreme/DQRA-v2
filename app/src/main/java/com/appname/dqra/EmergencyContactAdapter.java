package com.appname.dqra;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.EmergencyContact;

import java.util.ArrayList;
import java.util.List;

public class EmergencyContactAdapter
        extends RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder> {

    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1002;

    private final Context context;
    // Full master list — never modified after load
    private final List<EmergencyContact> masterList = new ArrayList<>();
    // Currently displayed (filtered) list
    private final List<EmergencyContact> displayList = new ArrayList<>();

    public EmergencyContactAdapter(Context context) {
        this.context = context;
    }

    // ─── Data ────────────────────────────────────────────────────────────────

    /** Replace data and reset filter. */
    public void setData(List<EmergencyContact> contacts) {
        masterList.clear();
        masterList.addAll(contacts);
        displayList.clear();
        displayList.addAll(contacts);
        notifyDataSetChanged();
    }

    /**
     * Filter by barangay/municipality (case-insensitive contains).
     * Pass empty string to clear the filter.
     */
    public void filter(String query) {
        displayList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayList.addAll(masterList);
        } else {
            String lower = query.trim().toLowerCase();
            for (EmergencyContact c : masterList) {
                if (c.getBarangay() != null
                        && c.getBarangay().toLowerCase().contains(lower)) {
                    displayList.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    // ─── RecyclerView overrides ───────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyContact contact = displayList.get(position);

        holder.tvStationName.setText(
                contact.getStationName() != null ? contact.getStationName() : "—");
        holder.tvBarangay.setText(
                contact.getBarangay() != null ? contact.getBarangay() : "—");
        holder.tvPhone.setText(
                contact.getPhoneNumber() != null ? contact.getPhoneNumber() : "—");

        holder.btnCall.setOnClickListener(v ->
                makePhoneCall(contact.getPhoneNumber()));
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // ─── Phone call (same pattern as UserAdapter) ─────────────────────────────

    private void makePhoneCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    (Activity) context,
                    new String[]{Manifest.permission.CALL_PHONE},
                    CALL_PHONE_PERMISSION_REQUEST_CODE);
            Toast.makeText(context,
                    "Phone call permission needed for emergency calls",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            context.startActivity(callIntent);
        } catch (Exception e) {
            Toast.makeText(context,
                    "Unable to make call: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.e("EmergencyContactAdapter", "Error making call: " + e.getMessage());
        }
    }

    // ─── ViewHolder ──────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStationName, tvBarangay, tvPhone;
        Button   btnCall;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStationName = itemView.findViewById(R.id.tvContactStationName);
            tvBarangay    = itemView.findViewById(R.id.tvContactBarangay);
            tvPhone       = itemView.findViewById(R.id.tvContactPhone);
            btnCall       = itemView.findViewById(R.id.btnCallContact);
        }
    }
}
