package com.appname.dqra;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.appname.dqra.mainregister.MainAdminRegister;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ISUFSTMAIN - Main landing screen.
 * Provides navigation to Register Admin or Logout.
 */
public class ISUFSTMAIN extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // CardView acting as the Register Admin button
        CardView btnRegister = findViewById(R.id.btn1);
        CardView btnEmergencyContacts = findViewById(R.id.btnManageEmergencyContacts);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(ISUFSTMAIN.this, MainAdminRegister.class);
            startActivity(intent);
        });

        btnEmergencyContacts.setOnClickListener(v -> {
            Intent intent = new Intent(ISUFSTMAIN.this, AddEmergencyContactActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> logoutUser());
    }

    /**
     * Signs out the current user and redirects to Login screen.
     */
    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
