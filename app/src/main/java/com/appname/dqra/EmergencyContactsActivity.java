package com.appname.dqra;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.EmergencyContact;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * EmergencyContactsActivity
 *
 * User-facing screen that displays emergency contacts fetched from Firestore.
 * Features a search bar that filters by municipality / barangay,
 * and a Call button on each item.
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    private static final String COLLECTION = "emergency_contacts";

    private RecyclerView         recyclerView;
    private EmergencyContactAdapter adapter;
    private TextView             tvEmpty;
    private EditText             etSearch;
    private FirebaseFirestore    db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        db = FirebaseFirestore.getInstance();

        ImageButton btnBack = findViewById(R.id.btnEmergencyContactsBack);
        tvEmpty      = findViewById(R.id.tvEmptyContacts);
        etSearch     = findViewById(R.id.etSearchLocation);
        recyclerView = findViewById(R.id.rvEmergencyContacts);

        btnBack.setOnClickListener(v -> finish());

        // Setup RecyclerView
        adapter = new EmergencyContactAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Search / filter listener
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                adapter.filter(s.toString());
                updateEmptyState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadContacts();
    }

    // ─── Load from Firestore ─────────────────────────────────────────────────

    private void loadContacts() {
        db.collection(COLLECTION)
                .orderBy("barangay")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<EmergencyContact> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        EmergencyContact contact = doc.toObject(EmergencyContact.class);
                        if (contact != null) {
                            contact.setId(doc.getId());
                            list.add(contact);
                        }
                    }
                    adapter.setData(list);
                    updateEmptyState();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load contacts: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
