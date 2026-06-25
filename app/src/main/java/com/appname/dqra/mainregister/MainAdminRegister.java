package com.appname.dqra.mainregister;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.appname.dqra.ISUFSTMAIN;
import com.appname.dqra.LocationUtils;
import com.appname.dqra.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainAdminRegister extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 201;
    private static final String ADMIN_REGISTRATION_APP_NAME = "AdminRegistrationApp";

    // UI Components
    private EditText edtEmail, edtPassword, edtStationName, edtStationPhone, edtStationPhone2, edtFacebookPage;
    private AutoCompleteTextView edtMunicipality;
    private ImageView imgTogglePassword;
    private ImageView btnLogout;
    private Button btnRegister;
    private ProgressBar progressBar;

    // Firebase Components
    private FirebaseAuth auth;
    private FirebaseAuth adminRegistrationAuth;
    private FirebaseFirestore db;

    // Location Utility
    private LocationUtils locationUtils;

    // ✅ NEW: Track password visibility state
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.adminregistration);

        // Initialize UI components
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        imgTogglePassword = findViewById(R.id.imgTogglePassword);
        edtMunicipality = findViewById(R.id.edtMunicipality);
        edtStationName = findViewById(R.id.edtStationName);
        edtStationPhone = findViewById(R.id.edtStationPhone);
        edtStationPhone2 = findViewById(R.id.edtStationPhone2);
        edtFacebookPage = findViewById(R.id.edtFacebookPage);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogout = findViewById(R.id.btnLogout);
        progressBar = findViewById(R.id.progressBar);

        // Initialize Firebase services
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize LocationUtils
        locationUtils = new LocationUtils(this);

        // Setup password visibility toggle
        setupPasswordToggle();

        // Setup municipality autocomplete with all Iloilo towns/cities
        setupMunicipalityAutocomplete();

        // Button listeners
        btnRegister.setOnClickListener(v -> registerAdminAndRescueStation());
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    /**
     * Handles user logout / back navigation
     */
    private void logoutUser() {
        Intent intent = new Intent(MainAdminRegister.this, ISUFSTMAIN.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /**
     * Validates input fields and checks for duplicate email before registering
     */
    private void registerAdminAndRescueStation() {
        String email       = edtEmail.getText().toString().trim();
        String password    = edtPassword.getText().toString().trim();
        String municipality = edtMunicipality.getText().toString().trim();
        String stationName = edtStationName.getText().toString().trim();
        String stationPhone  = edtStationPhone.getText().toString().trim();
        String stationPhone2 = edtStationPhone2.getText().toString().trim(); // Optional
        String facebookPage  = edtFacebookPage.getText().toString().trim();  // Optional

        // Validate required fields (phone 2 is optional)
        if (email.isEmpty() || password.isEmpty() || municipality.isEmpty()
                || stationName.isEmpty() || stationPhone.isEmpty()) {
            Toast.makeText(this, "All fields except Phone Number 2 are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate municipality is from the Iloilo list
        if (!isValidIloiloMunicipality(municipality)) {
            Toast.makeText(this, "Please select a valid municipality from the list!", Toast.LENGTH_SHORT).show();
            edtMunicipality.requestFocus();
            edtMunicipality.showDropDown();
            return;
        }

        // ✅ FIXED: Add email format validation
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ FIXED: Add password strength validation
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ FIXED: Add phone number validation
        if (stationPhone.length() < 10) {
            Toast.makeText(this, "Please enter a valid phone number (at least 10 digits)!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }

        progressBar.setVisibility(ProgressBar.VISIBLE);

        // Check if admin with this email already exists
        db.collection("admins_with_stations")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Email is unique — proceed with registration
                            createAdminAccount(email, password, municipality, stationName, stationPhone, stationPhone2, facebookPage);
                        } else {
                            progressBar.setVisibility(ProgressBar.GONE);
                            Toast.makeText(this, "Admin with this email already exists!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        progressBar.setVisibility(ProgressBar.GONE);
                        Toast.makeText(this, "Error checking admin existence: "
                                + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }

            if (granted) {
                registerAdminAndRescueStation();
            } else {
                progressBar.setVisibility(ProgressBar.GONE);
                Toast.makeText(this,
                        "Location permission is required to save the rescue station location.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Creates Firebase Auth account and saves all data (including both phone numbers) to Firestore
     */
    private void createAdminAccount(String email, String password, String municipality,
                                    String stationName, String stationPhone, String stationPhone2,
                                    String facebookPage) {

        FirebaseAuth registrationAuth = getAdminRegistrationAuth();

        registrationAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser registeredAdmin = registrationAuth.getCurrentUser();
                        if (registeredAdmin == null) {
                            progressBar.setVisibility(ProgressBar.GONE);
                            Toast.makeText(MainAdminRegister.this,
                                    "Admin account was not created correctly.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String userId = registeredAdmin.getUid();

                        // Get current device location for the rescue station
                        locationUtils.getCurrentLocation(new LocationUtils.OnLocationFetchedListener() {
                            @Override
                            public void onLocationFetched(double latitude, double longitude) {

                                if (latitude == 0 && longitude == 0) {
                                    progressBar.setVisibility(ProgressBar.GONE);
                                    Toast.makeText(MainAdminRegister.this,
                                            "Unable to fetch location!", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Build Firestore document
                                Map<String, Object> adminWithStationData = new HashMap<>();
                                adminWithStationData.put("adminId",      userId);
                                adminWithStationData.put("email",         email);
                                adminWithStationData.put("role",          "Admin");
                                adminWithStationData.put("municipality",  municipality);
                                adminWithStationData.put("stationName",   stationName);
                                adminWithStationData.put("stationPhone",  stationPhone);
                                adminWithStationData.put("stationPhone2", stationPhone2); // May be empty string
                                adminWithStationData.put("facebookPage",  facebookPage);  // May be empty string
                                adminWithStationData.put("latitude",      latitude);
                                adminWithStationData.put("longitude",     longitude);
                                adminWithStationData.put("isVerified",    false);

                                // Save to Firestore
                                db.collection("admins_with_stations").document(userId)
                                        .set(adminWithStationData)
                                        .addOnSuccessListener(aVoid -> {
                                            registrationAuth.signOut();
                                            progressBar.setVisibility(ProgressBar.GONE);
                                            Toast.makeText(MainAdminRegister.this,
                                                    "Admin and Rescue Station Registered!",
                                                    Toast.LENGTH_SHORT).show();
                                            Intent intent = new Intent(MainAdminRegister.this, ISUFSTMAIN.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(intent);
                                        })
                                        .addOnFailureListener(e -> {
                                            registrationAuth.signOut();
                                            progressBar.setVisibility(ProgressBar.GONE);
                                            Toast.makeText(MainAdminRegister.this,
                                                    "Error saving data: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                            Log.e("FirestoreError", "Error saving data: " + e.getMessage());
                                        });
                            }
                        });

                    } else {
                        registrationAuth.signOut();
                        progressBar.setVisibility(ProgressBar.GONE);
                        Log.e("AuthError", "Registration failed: " + task.getException().getMessage());
                        Toast.makeText(this, "Registration Failed: "
                                + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private FirebaseAuth getAdminRegistrationAuth() {
        if (adminRegistrationAuth != null) {
            return adminRegistrationAuth;
        }

        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(ADMIN_REGISTRATION_APP_NAME);
        } catch (IllegalStateException ignored) {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            app = FirebaseApp.initializeApp(this, options, ADMIN_REGISTRATION_APP_NAME);
        }

        adminRegistrationAuth = FirebaseAuth.getInstance(app);
        return adminRegistrationAuth;
    }

    /**
     * Checks if the entered municipality is a valid Iloilo municipality/city.
     */
    private boolean isValidIloiloMunicipality(String input) {
        String[] valid = {
                "Iloilo City", "Passi City", "Ajuy", "Alimodian", "Anilao", "Badiangan",
                "Balasan", "Banate", "Barotac Nuevo", "Barotac Viejo", "Batad", "Bingawan",
                "Cabatuan", "Calinog", "Carles", "Concepcion", "Dingle", "Dueñas", "Dumangas",
                "Estancia", "Guimbal", "Igbaras", "Janiuay", "Lambunao", "Leganes", "Lemery",
                "Leon", "Maasin", "Miagao", "Mina", "New Lucena", "Oton", "Pavia", "Pototan",
                "San Dionisio", "San Enrique", "San Joaquin", "San Miguel", "San Rafael",
                "Santa Barbara", "Sara", "Tigbauan", "Tubungan", "Zarraga"
        };
        for (String v : valid) {
            if (v.equalsIgnoreCase(input)) return true;
        }
        return false;
    }

    /**
     * Sets up AutoCompleteTextView with all municipalities and cities of Iloilo province.
     */
    private void setupMunicipalityAutocomplete() {
        String[] iloiloMunicipalities = {
                // === Cities ===
                "Iloilo City",
                "Passi City",
                // === Municipalities ===
                "Ajuy",
                "Alimodian",
                "Anilao",
                "Badiangan",
                "Balasan",
                "Banate",
                "Barotac Nuevo",
                "Barotac Viejo",
                "Batad",
                "Bingawan",
                "Cabatuan",
                "Calinog",
                "Carles",
                "Concepcion",
                "Dingle",
                "Dueñas",
                "Dumangas",
                "Estancia",
                "Guimbal",
                "Igbaras",
                "Janiuay",
                "Lambunao",
                "Leganes",
                "Lemery",
                "Leon",
                "Maasin",
                "Miagao",
                "Mina",
                "New Lucena",
                "Oton",
                "Pavia",
                "Pototan",
                "San Dionisio",
                "San Enrique",
                "San Joaquin",
                "San Miguel",
                "San Rafael",
                "Santa Barbara",
                "Sara",
                "Tigbauan",
                "Tubungan",
                "Zarraga"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                iloiloMunicipalities
        );
        edtMunicipality.setAdapter(adapter);

        // Dismiss dropdown when a suggestion is selected
        edtMunicipality.setOnItemClickListener((parent, view, position, id) ->
                edtMunicipality.dismissDropDown()
        );
    }

    // Password visibility toggle functionality
    private void setupPasswordToggle() {
        if (imgTogglePassword != null) {
            imgTogglePassword.setOnClickListener(v -> {
                if (isPasswordVisible) {
                    // Hide password
                    edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    imgTogglePassword.setImageResource(R.drawable.ic_visibility_off);
                    isPasswordVisible = false;
                } else {
                    // Show password
                    edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    imgTogglePassword.setImageResource(R.drawable.ic_visibility);
                    isPasswordVisible = true;
                }
                // Move cursor to end of text
                edtPassword.setSelection(edtPassword.getText().length());
            });
        }
    }
}
