package com.appname.dqra;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int BACKGROUND_LOCATION_REQUEST_CODE = 102;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String FIRST_LAUNCH_KEY = "isFirstLaunch";
    private boolean waitingForNotificationSettings = false;
    private boolean notificationDialogShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if it's the first launch
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(FIRST_LAUNCH_KEY, true);

        if (isFirstLaunch) {
            // Mark that the app has been launched before
            prefs.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply();

            // Redirect to RegisterActivity (first-time only)
            redirectToRegister();
        } else {
            // Normal flow: request permissions then go to LoginActivity
            requestPermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForNotificationSettings) {
            waitingForNotificationSettings = false;
            checkNotificationPermission();
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE);
        } else {
            requestBackgroundLocationIfNeeded();
        }
    }

    /**
     * ACCESS_BACKGROUND_LOCATION must be requested SEPARATELY on Android 10+ (API 29+).
     * Requesting it together with other permissions causes a flicker / silent denial.
     */
    private void requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("Background Location")
                        .setMessage("DQRA needs to access your location in the background to monitor emergencies even when the app is closed.\n\nPlease select \"Allow all the time\" on the next screen.")
                        .setPositiveButton("Continue", (dialog, which) ->
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                        BACKGROUND_LOCATION_REQUEST_CODE))
                        .setNegativeButton("Skip", (dialog, which) -> checkNotificationPermission())
                        .setCancelable(false)
                        .show();
                return;
            }
        }
        checkNotificationPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check RECEIVE_SMS and READ_PHONE_STATE were granted (indices 0 and 1)
            boolean smsGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean phoneGranted = grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED;

            if (smsGranted && phoneGranted) {
                // Fine location may or may not be granted — proceed either way, then ask background location
                requestBackgroundLocationIfNeeded();
            } else {
                Toast.makeText(this, "SMS and Phone State permissions are required!", Toast.LENGTH_LONG).show();
                finish();
            }
        } else if (requestCode == BACKGROUND_LOCATION_REQUEST_CODE) {
            // Background location granted or denied — either way continue to notification check
            checkNotificationPermission();
        }
    }

    /**
     * NEW: Check and request notification listener permission
     * This is CRITICAL for Messenger alert detection
     */
    private void checkNotificationPermission() {
        if (!isNotificationServiceEnabled()) {
            showNotificationPermissionDialog();
        } else {
            // Notification access is enabled, proceed to login
            redirectToLogin();
        }
    }

    private void showNotificationPermissionDialog() {
        if (notificationDialogShowing || isFinishing()) {
            return;
        }

        notificationDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle("🔔 Notification Access Required")
                .setMessage("To detect emergency coordinates from Messenger, DQRA needs notification access.\n\nThis allows the app to:\n• Read Messenger notifications\n• Detect 'LOC: latitude, longitude' patterns\n• Trigger emergency alerts automatically\n\nYour messages remain private - we only scan for location patterns.")
                .setPositiveButton("Enable Now", (dialog, which) -> {
                    notificationDialogShowing = false;
                    requestNotificationListenerPermission();
                })
                .setNegativeButton("Skip for Now", (dialog, which) -> {
                    notificationDialogShowing = false;
                    Toast.makeText(this, "You can enable this later in Settings", Toast.LENGTH_SHORT).show();
                    redirectToLogin(); // Still allow login without it
                })
                .setCancelable(false)
                .setOnDismissListener(dialog -> notificationDialogShowing = false)
                .show();
    }

    private void requestNotificationListenerPermission() {
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this,
                    "Please enable 'DQRA' in the Notification Access settings",
                    Toast.LENGTH_LONG).show();

            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            waitingForNotificationSettings = true;
            startActivity(intent);

            // Don't finish() - let user return to this activity after enabling
        } else {
            redirectToLogin();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean isEnabled = flat != null && flat.contains(pkgName);
        Log.d("DQRA", "Notification service enabled: " + isEnabled);
        return isEnabled;
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void redirectToRegister() {
        startActivity(new Intent(this, RegisterActivity.class));
        finish();
    }
}
