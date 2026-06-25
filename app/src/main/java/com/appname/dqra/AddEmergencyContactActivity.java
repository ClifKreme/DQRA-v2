package com.appname.dqra;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * AddEmergencyContactActivity
 *
 * Accessible from ISUFSTMAIN (main role).
 * Allows the main role to register emergency contacts
 * (municipality/barangay name, station name, phone number)
 * which are stored in the "emergency_contacts" Firestore collection.
 */
public class AddEmergencyContactActivity extends AppCompatActivity {

    private static final String COLLECTION = "emergency_contacts";

    private EditText etBarangay, etStationName, etPhone;
    private Button   btnSave;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_emergency_contact);

        db = FirebaseFirestore.getInstance();

        ImageButton btnBack = findViewById(R.id.btnAddContactBack);
        etBarangay    = findViewById(R.id.etContactBarangay);
        etStationName = findViewById(R.id.etContactStationName);
        etPhone       = findViewById(R.id.etContactPhone);
        btnSave       = findViewById(R.id.btnSaveContact);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveContact());
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    private void saveContact() {
        String barangay    = etBarangay.getText().toString().trim();
        String stationName = etStationName.getText().toString().trim();
        String phone       = etPhone.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(barangay)) {
            etBarangay.setError("Municipality / Barangay is required");
            etBarangay.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(stationName)) {
            etStationName.setError("Station name is required");
            etStationName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number is required");
            etPhone.requestFocus();
            return;
        }
        if (phone.length() < 7) {
            etPhone.setError("Enter a valid phone number");
            etPhone.requestFocus();
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("barangay",    barangay);
        data.put("stationName", stationName);
        data.put("phoneNumber", phone);

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Emergency contact saved!", Toast.LENGTH_SHORT).show();
                    clearFields();
                    btnSave.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                });
    }

    private void clearFields() {
        etBarangay.setText("");
        etStationName.setText("");
        etPhone.setText("");
        etBarangay.requestFocus();
    }
}
