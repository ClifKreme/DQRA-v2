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
import com.appname.dqra.repository.AdminRegistrationRepository;

public class MainAdminRegister extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 201;

    private EditText edtEmail, edtPassword, edtStationName, edtStationPhone, edtStationPhone2, edtFacebookPage;
    private AutoCompleteTextView edtMunicipality;
    private ImageView imgTogglePassword;
    private ImageView btnLogout;
    private Button btnRegister;
    private ProgressBar progressBar;

    private AdminRegistrationRepository adminRegistrationRepository;
    private LocationUtils locationUtils;
    private boolean isPasswordVisible = false;

    private static final String[] ILOILO_MUNICIPALITIES = {
            "Iloilo City", "Passi City", "Ajuy", "Alimodian", "Anilao", "Badiangan",
            "Balasan", "Banate", "Barotac Nuevo", "Barotac Viejo", "Batad", "Bingawan",
            "Cabatuan", "Calinog", "Carles", "Concepcion", "Dingle", "Dueñas", "Dumangas",
            "Estancia", "Guimbal", "Igbaras", "Janiuay", "Lambunao", "Leganes", "Lemery",
            "Leon", "Maasin", "Miagao", "Mina", "New Lucena", "Oton", "Pavia", "Pototan",
            "San Dionisio", "San Enrique", "San Joaquin", "San Miguel", "San Rafael",
            "Santa Barbara", "Sara", "Tigbauan", "Tubungan", "Zarraga"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.adminregistration);

        adminRegistrationRepository = new AdminRegistrationRepository(this);
        locationUtils = new LocationUtils(this);

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

        setupPasswordToggle();
        setupMunicipalityAutocomplete();

        btnRegister.setOnClickListener(v -> registerAdminAndRescueStation());
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    private void logoutUser() {
        Intent intent = new Intent(MainAdminRegister.this, ISUFSTMAIN.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void registerAdminAndRescueStation() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String municipality = edtMunicipality.getText().toString().trim();
        String stationName = edtStationName.getText().toString().trim();
        String stationPhone = edtStationPhone.getText().toString().trim();
        String stationPhone2 = edtStationPhone2.getText().toString().trim();
        String facebookPage = edtFacebookPage.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || municipality.isEmpty()
                || stationName.isEmpty() || stationPhone.isEmpty()) {
            Toast.makeText(this, "All fields except Phone Number 2 are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidIloiloMunicipality(municipality)) {
            Toast.makeText(this, "Please select a valid municipality from the list!", Toast.LENGTH_SHORT).show();
            edtMunicipality.requestFocus();
            edtMunicipality.showDropDown();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (stationPhone.length() < 10) {
            Toast.makeText(this, "Please enter a valid phone number (at least 10 digits)!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }

        progressBar.setVisibility(ProgressBar.VISIBLE);
        adminRegistrationRepository.isAdminEmailAvailable(email,
                new AdminRegistrationRepository.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean available) {
                        if (Boolean.TRUE.equals(available)) {
                            createAdminAccount(email, password, municipality, stationName,
                                    stationPhone, stationPhone2, facebookPage);
                        } else {
                            progressBar.setVisibility(ProgressBar.GONE);
                            Toast.makeText(MainAdminRegister.this,
                                    "Admin with this email already exists!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        progressBar.setVisibility(ProgressBar.GONE);
                        Toast.makeText(MainAdminRegister.this,
                                "Error checking admin existence: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
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

        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

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

    private void createAdminAccount(String email, String password, String municipality,
                                    String stationName, String stationPhone, String stationPhone2,
                                    String facebookPage) {
        locationUtils.getCurrentLocation(new LocationUtils.OnLocationFetchedListener() {
            @Override
            public void onLocationFetched(double latitude, double longitude) {
                if (latitude == 0 && longitude == 0) {
                    progressBar.setVisibility(ProgressBar.GONE);
                    Toast.makeText(MainAdminRegister.this,
                            "Unable to fetch location!", Toast.LENGTH_SHORT).show();
                    return;
                }

                adminRegistrationRepository.registerAdminWithStation(
                        email, password, municipality, stationName, stationPhone,
                        stationPhone2, facebookPage, latitude, longitude,
                        new AdminRegistrationRepository.Callback<Void>() {
                            @Override
                            public void onSuccess(Void value) {
                                progressBar.setVisibility(ProgressBar.GONE);
                                Toast.makeText(MainAdminRegister.this,
                                        "Admin and Rescue Station Registered!",
                                        Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(MainAdminRegister.this, ISUFSTMAIN.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            }

                            @Override
                            public void onError(Exception exception) {
                                progressBar.setVisibility(ProgressBar.GONE);
                                Log.e("AdminRegister", "Registration failed: " + exception.getMessage());
                                Toast.makeText(MainAdminRegister.this,
                                        "Registration Failed: " + exception.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }

    private boolean isValidIloiloMunicipality(String input) {
        for (String municipality : ILOILO_MUNICIPALITIES) {
            if (municipality.equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }

    private void setupMunicipalityAutocomplete() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                ILOILO_MUNICIPALITIES
        );
        edtMunicipality.setAdapter(adapter);
        edtMunicipality.setOnItemClickListener((parent, view, position, id) ->
                edtMunicipality.dismissDropDown());
    }

    private void setupPasswordToggle() {
        if (imgTogglePassword == null) {
            return;
        }

        imgTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                imgTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                imgTogglePassword.setImageResource(R.drawable.ic_visibility);
            }
            isPasswordVisible = !isPasswordVisible;
            edtPassword.setSelection(edtPassword.getText().length());
        });
    }
}
