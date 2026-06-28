package com.appname.dqra;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.appname.dqra.repository.UserRegistrationRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class PendingApprovalActivity extends AppCompatActivity {

    private UserRegistrationRepository repository;
    private ListenerRegistration listenerRegistration;
    private TextView tvPendingStatus;
    private boolean hasNavigated = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approval);

        repository = new UserRegistrationRepository(this);
        tvPendingStatus = findViewById(R.id.tvPendingStatus);

        listenForApproval();
    }

    @Override
    protected void onDestroy() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        super.onDestroy();
    }

    private void listenForApproval() {
        listenerRegistration = repository.listenForCurrentUserRequestStatus(
                new UserRegistrationRepository.StatusListener() {
                    @Override
                    public void onStatusChanged(String status) {
                        if (hasNavigated) {
                            return;
                        }

                        if ("approved".equalsIgnoreCase(status)) {
                            hasNavigated = true;
                            Toast.makeText(PendingApprovalActivity.this,
                                    "Registration approved", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(PendingApprovalActivity.this, ProfileCheckActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else if ("rejected".equalsIgnoreCase(status)) {
                            hasNavigated = true;
                            repository.signOut();
                            new AlertDialog.Builder(PendingApprovalActivity.this)
                                    .setTitle("Registration Rejected")
                                    .setMessage("Your registration request was rejected. Please contact an admin or submit a new request.")
                                    .setPositiveButton("OK", (dialog, which) -> goToLogin())
                                    .setCancelable(false)
                                    .show();
                        } else {
                            tvPendingStatus.setText("Waiting for admin approval");
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        tvPendingStatus.setText("Still waiting for admin approval");
                    }
                });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
