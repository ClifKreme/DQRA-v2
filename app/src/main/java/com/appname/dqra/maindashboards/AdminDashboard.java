package com.appname.dqra.maindashboards;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.appname.dqra.AdminProfileActivity;
import com.appname.dqra.BaseActivity;
import com.appname.dqra.admin.AdminDashboardUiState;
import com.appname.dqra.admin.AdminDashboardViewModel;
import com.appname.dqra.admin.AdminEmergency;
import com.appname.dqra.admin.AdminEmergencyStats;
import com.appname.dqra.admin.AdminEmergencyStreamState;
import com.appname.dqra.EmergencyLogsAdapter;
import com.appname.dqra.image.FullScreenImageActivity;
import com.appname.dqra.listeners.AlertService;
import com.appname.dqra.listeners.FirestoreService;
import com.appname.dqra.LoginActivity;
import com.appname.dqra.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdminDashboard extends BaseActivity {
    private static final String TAG = "AdminRescueActivity";
    private ListenerRegistration imageUpdateListener;
    private AdminDashboardViewModel viewModel;

    private static final String EMERGENCY_PREFS = "emergency_preferences";
    private static final String ALERTED_EMERGENCIES_KEY = "alerted_emergencies";
    private static final String CURRENT_EMERGENCY_KEY = "current_emergency_id";
    private static final String CURRENT_EMERGENCY_LAT_KEY = "current_emergency_lat";
    private static final String CURRENT_EMERGENCY_LNG_KEY = "current_emergency_lng";
    private static final String CURRENT_EMERGENCY_PHONE_KEY = "current_emergency_phone";
    private static final String CURRENT_EMERGENCY_REPORTED_KEY = "current_emergency_reported";
    private static final String CURRENT_EMERGENCY_MUNICIPALITY_KEY = "current_emergency_municipality";
    private static final String CURRENT_EMERGENCY_STATUS_KEY = "current_emergency_status";

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ACKNOWLEDGED = "acknowledged";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_RESOLVED = "resolved"; // ✅ NEW constant

    private static final int NOTIFICATION_ID_EMERGENCY_ALERT = 1001;
    private static final int NOTIFICATION_ID_EMERGENCY_RESOLVED = 1002;
    private static final int NOTIFICATION_ID_WEBSITE = 1003;
    private static final int NOTIFICATION_ID_VOLUME_WARNING = 1004;
    private static final int PERMISSION_REQUEST_LOCATION = 100;
    private static final int PERMISSION_REQUEST_NOTIFICATIONS = 101;
    private static final int PERMISSION_STEP_LOCATION = 0;
    private static final int PERMISSION_STEP_NOTIFICATIONS = 1;
    private static final int PERMISSION_STEP_OVERLAY = 2;
    private static final int PERMISSION_STEP_DND = 3;
    private static final int PERMISSION_STEP_NOTIFICATION_LISTENER = 4;
    private static final int PERMISSION_STEP_DONE = 5;
    private int permissionFlowStep = PERMISSION_STEP_DONE;
    private boolean permissionFlowActive = false;
    private boolean waitingForPermissionSettings = false;

    // UI elements
    private Button btnViewMap, btnLogout, btnLogs, btnStopSound, btnCallSender;
    private Button btnConfirm, btnReject, btnViewImage;
    private Button btnShareMessenger; // ✅ NEW
    private TextView tvEmergencyAlert;
    private TextView tvPendingCount, tvActiveCount, tvResolvedCount;
    private TextView tvStatusIcon, tvEmergencyTime, tvSenderName;
    private TextView tvEmergencyActionsHeader, tvInfoHeader;
    private TextView tvAdminMunicipality;

    private CardView cardEmergencyStatus;

    // Emergency location data
    private double emergencyLatitude;
    private double emergencyLongitude;
    private String Phone;
    private String Reported;
    private String emergencyMunicipality;
    private String currentEmergencyStatus;

    // Image data
    private String currentEmergencyImageBase64 = "";
    private boolean currentEmergencyHasImage = false;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CollectionReference emergencyLocationRef;
    private String adminMunicipality = "";
    private String currentEmergencyId = "";
    private ListenerRegistration emergencyLocationListener;

    // Alert system
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private SharedPreferences sharedPreferences;
    private Set<String> alertedEmergencies;
    private static final String TARGET_MUNICIPALITY = "Barotac Nuevo";
    private ListenerRegistration websiteNotificationListener;
    private AudioManager audioManager;
    private int previousAlarmVolume = 0;
    private volatile boolean isPlayingAlert = false; // ✅ NEW: prevent duplicate alert race condition

    // Sound monitoring
    private Handler soundCheckHandler;
    private Runnable soundCheckRunnable;
    private static final int SOUND_CHECK_INTERVAL = 500;

    // ✅ NEW: Debounce flag to prevent double-send on Messenger share
    private boolean isShareInProgress = false;

    // Location
    private FusedLocationProviderClient fusedLocationProviderClient;

    // Messenger BroadcastReceiver
    private BroadcastReceiver messengerAlertReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("EMERGENCY_ALERT_RECEIVED".equals(intent.getAction())) {
                String latStr = intent.getStringExtra("latitude");
                String lonStr = intent.getStringExtra("longitude");
                String source = intent.getStringExtra("source");

                if ("messenger".equals(source) && latStr != null && lonStr != null) {
                    try {
                        emergencyLatitude = Double.parseDouble(latStr);
                        emergencyLongitude = Double.parseDouble(lonStr);
                        Phone = firstNonEmpty(intent.getStringExtra("phoneNumber"), "Via Messenger");
                        Reported = firstNonEmpty(intent.getStringExtra("reportedBy"), "Messenger User");
                        String messageText = intent.getStringExtra("messageText");
                        emergencyMunicipality = adminMunicipality != null ? adminMunicipality : "Unknown";
                        currentEmergencyStatus = STATUS_PENDING;

                        currentEmergencyId = "messenger_" + System.currentTimeMillis();
                        currentEmergencyHasImage = false;
                        currentEmergencyImageBase64 = "";

                        saveCurrentEmergencyToPrefs();
                        markEmergencyAsAlerted(currentEmergencyId);
                        showEmergencyAlert();

                        Log.d(TAG, "Messenger emergency received: " + latStr + ", " + lonStr +
                                " from: " + Reported + " (Message: " + messageText + ")");

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid coordinates from Messenger: " + latStr + ", " + lonStr, e);
                        Toast.makeText(AdminDashboard.this,
                                "Invalid emergency location received",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_admin_rescue);
        setActiveTab(Tab.HOME);

        viewModel = new ViewModelProvider(this).get(AdminDashboardViewModel.class);
        createNotificationChannel();
        initializeFirebase();
        observeViewModel();
        loadAlertedEmergencies();
        loadCurrentEmergencyFromPrefs();
        initializeUI();
        initializeVibrator();
        initializeLocationClient();
        fetchAdminMunicipality();
        handleIntent(getIntent());
        startFirestoreService();
        listenForWebsiteNotifications();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messengerAlertReceiver,
                    new IntentFilter("EMERGENCY_ALERT_RECEIVED"),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(messengerAlertReceiver,
                    new IntentFilter("EMERGENCY_ALERT_RECEIVED"),
                    Context.RECEIVER_EXPORTED);
        }

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);

        if (firstLaunch) {
            requestAllPermissions();
            requestBatteryOptimizationExemption();
            prefs.edit().putBoolean("first_launch", false).apply();
        }

        if (STATUS_PENDING.equals(currentEmergencyStatus) &&
                currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
            setStatisticsCardsEnabled(false);
        } else {
            setStatisticsCardsEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (permissionFlowActive && waitingForPermissionSettings) {
            waitingForPermissionSettings = false;
            requestNextPermissionStep();
        }

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("lat") && intent.hasExtra("lon")) {
            handleIntent(intent);
            intent.removeExtra("lat");
            intent.removeExtra("lon");
        } else {
            if (!viewModel.hasCurrentUser()) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
            fetchAdminMunicipality();

            if (adminMunicipality != null && !adminMunicipality.isEmpty() &&
                    (emergencyLocationListener == null)) {
                listenForEmergencyLocationChanges();
            }

            if (currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
                restoreEmergencyUI();

                if (STATUS_CONFIRMED.equals(currentEmergencyStatus)) {
                    startImageUpdateListener(currentEmergencyId);
                }
            }

            if (adminMunicipality != null && !adminMunicipality.isEmpty()) {
                updateEmergencyStatistics();
            }
        }

        startSoundMonitoring();
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenForWebsiteNotifications();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (websiteNotificationListener != null) {
            websiteNotificationListener.remove();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopSoundMonitoring();
        stopEmergencyAlerts();

        if (imageUpdateListener != null) {
            imageUpdateListener.remove();
            imageUpdateListener = null;
        }
        saveCurrentEmergencyToPrefs();
    }

    @Override
    protected void onDestroy() {
        stopSoundMonitoring();
        stopEmergencyAlerts();

        if (messengerAlertReceiver != null) {
            try {
                unregisterReceiver(messengerAlertReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver already unregistered", e);
            }
        }

        if (emergencyLocationListener != null) {
            emergencyLocationListener.remove();
            emergencyLocationListener = null;
        }
        if (websiteNotificationListener != null) {
            websiteNotificationListener.remove();
            websiteNotificationListener = null;
        }
        if (imageUpdateListener != null) {
            imageUpdateListener.remove();
            imageUpdateListener = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    // ============== SOUND MONITORING SYSTEM ==============

    private boolean isAlertPlaying() {
        boolean mediaPlayerPlaying = (mediaPlayer != null && mediaPlayer.isPlaying());
        boolean vibratorActive = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibratorActive = (vibrator != null && vibrator.hasVibrator());
        }

        return mediaPlayerPlaying || vibratorActive;
    }

    private void startSoundMonitoring() {
        if (soundCheckHandler == null) {
            soundCheckHandler = new Handler(Looper.getMainLooper());
        }

        soundCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAlertPlaying()) {
                    runOnUiThread(() -> {
                        if (btnStopSound != null && btnStopSound.getVisibility() != View.VISIBLE) {
                            btnStopSound.setVisibility(View.VISIBLE);
                            Log.d(TAG, "✅ Sound detected - showing stop button");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (btnStopSound != null && btnStopSound.getVisibility() == View.VISIBLE) {
                            if (!STATUS_PENDING.equals(currentEmergencyStatus)) {
                                btnStopSound.setVisibility(View.GONE);
                                Log.d(TAG, "❌ No sound detected - hiding stop button");
                            }
                        }
                    });
                }

                if (soundCheckHandler != null) {
                    soundCheckHandler.postDelayed(this, SOUND_CHECK_INTERVAL);
                }
            }
        };

        soundCheckHandler.post(soundCheckRunnable);
        Log.d(TAG, "🎵 Sound monitoring started");
    }

    private void stopSoundMonitoring() {
        if (soundCheckHandler != null && soundCheckRunnable != null) {
            soundCheckHandler.removeCallbacks(soundCheckRunnable);
            Log.d(TAG, "🔇 Sound monitoring stopped");
        }
    }

    // ============== END OF SOUND MONITORING SYSTEM ==============

    private boolean isMessengerEmergency(String emergencyId) {
        return emergencyId != null && emergencyId.startsWith("messenger_");
    }

    private void restoreEmergencyUI() {
        if (currentEmergencyId == null || currentEmergencyId.isEmpty()) {
            return;
        }

        runOnUiThread(() -> {
            String statusText = getStatusDisplayText(currentEmergencyStatus);
            String imageText = currentEmergencyHasImage ? "\n📷 Image Available" : "";
            String sourceText = isMessengerEmergency(currentEmergencyId) ? "\n📱 From Messenger" : "";

            tvEmergencyAlert.setText(String.format(Locale.getDefault(),
                    "%s\n\n" +
                            "Reported By: %s\n" +
                            "Contact: %s\n" +
                            "Status: %s%s%s",
                    statusText, Reported, Phone, currentEmergencyStatus.toUpperCase(), imageText, sourceText));

            btnViewMap.setVisibility(View.VISIBLE);
            btnCallSender.setVisibility(View.VISIBLE);
            btnShareMessenger.setVisibility(View.VISIBLE); // ✅ NEW
            btnViewImage.setVisibility(currentEmergencyHasImage ? View.VISIBLE : View.GONE);

            Button btnComplete = findViewById(R.id.btnComplete);

            if (STATUS_PENDING.equals(currentEmergencyStatus)) {
                btnConfirm.setVisibility(View.VISIBLE);
                btnReject.setVisibility(View.VISIBLE);
                btnStopSound.setVisibility(View.VISIBLE);
                if (btnComplete != null) btnComplete.setVisibility(View.GONE);

                if (tvEmergencyActionsHeader != null) {
                    tvEmergencyActionsHeader.setVisibility(View.VISIBLE);
                }

                setStatisticsCardsEnabled(false);

            } else if (STATUS_CONFIRMED.equals(currentEmergencyStatus)) {
                btnConfirm.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnStopSound.setVisibility(View.GONE);

                if (btnComplete != null) {
                    btnComplete.setVisibility(View.VISIBLE);
                    btnComplete.setEnabled(true);
                    btnComplete.setText("✓ Mark as Resolved");
                }

                if (tvEmergencyActionsHeader != null) {
                    tvEmergencyActionsHeader.setVisibility(View.GONE);
                }

                setStatisticsCardsEnabled(true);

            } else {
                btnConfirm.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnStopSound.setVisibility(View.GONE);
                if (btnComplete != null) btnComplete.setVisibility(View.GONE);

                if (tvEmergencyActionsHeader != null) {
                    tvEmergencyActionsHeader.setVisibility(View.GONE);
                }

                setStatisticsCardsEnabled(true);
            }
        });
    }

    private void requestAllPermissions() {
        permissionFlowActive = true;
        permissionFlowStep = PERMISSION_STEP_LOCATION;
        waitingForPermissionSettings = false;
        requestNextPermissionStep();
    }

    private void requestNextPermissionStep() {
        if (!permissionFlowActive) {
            return;
        }

        while (permissionFlowStep < PERMISSION_STEP_DONE) {
            switch (permissionFlowStep) {
                case PERMISSION_STEP_LOCATION:
                    permissionFlowStep++;
                    if (requestLocationPermission()) {
                        return;
                    }
                    break;
                case PERMISSION_STEP_NOTIFICATIONS:
                    permissionFlowStep++;
                    if (requestNotificationPermission()) {
                        return;
                    }
                    break;
                case PERMISSION_STEP_OVERLAY:
                    permissionFlowStep++;
                    if (requestOverlayPermission()) {
                        return;
                    }
                    break;
                case PERMISSION_STEP_DND:
                    permissionFlowStep++;
                    if (requestDNDAccess()) {
                        return;
                    }
                    break;
                case PERMISSION_STEP_NOTIFICATION_LISTENER:
                    permissionFlowStep++;
                    requestNotificationListenerPermission();
                    permissionFlowActive = false;
                    return;
                default:
                    permissionFlowStep = PERMISSION_STEP_DONE;
                    break;
            }
        }

        permissionFlowActive = false;
    }

    private boolean requestDNDAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (!notificationManager.isNotificationPolicyAccessGranted()) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                waitingForPermissionSettings = true;
                startActivity(intent);
                Toast.makeText(this,
                        "Please grant Do Not Disturb access for emergency alerts",
                        Toast.LENGTH_LONG).show();
                return true;
            }
        }
        return false;
    }

    private boolean requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
            return true;
        }
        return false;
    }

    private boolean requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_NOTIFICATIONS);
                return true;
            }
        }
        return false;
    }

    private boolean requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                waitingForPermissionSettings = true;
                startActivityForResult(intent, 102);
                return true;
            }
        }
        return false;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 103);
            }
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS}, 104);
        }
    }

    private void ensureEmergencyVolume() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }

            if (audioManager == null) {
                Log.e(TAG, "AudioManager is null");
                return;
            }

            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);

            Log.d(TAG, "Current ALARM volume: " + previousAlarmVolume + "/" + maxVolume);

            handleDoNotDisturb();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                for (int i = previousAlarmVolume; i < maxVolume; i++) {
                    audioManager.adjustStreamVolume(
                            AudioManager.STREAM_ALARM,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND
                    );
                }
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
            }

            overrideSilentMode();

            Log.d(TAG, "Emergency volume set successfully to max: " + maxVolume);

        } catch (Exception e) {
            Log.e(TAG, "Error ensuring emergency volume: " + e.getMessage(), e);
            showVolumeWarningNotification();
        }
    }

    private void handleDoNotDisturb() {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    notificationManager.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_ALL);
                    Log.d(TAG, "DND bypassed for emergency alert");
                } else {
                    Log.w(TAG, "No DND access permission - showing warning");
                    showDNDWarning();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling DND: " + e.getMessage());
        }
    }

    private void overrideSilentMode() {
        try {
            if (audioManager == null) return;

            int ringerMode = audioManager.getRingerMode();

            if (ringerMode == AudioManager.RINGER_MODE_SILENT ||
                    ringerMode == AudioManager.RINGER_MODE_VIBRATE) {

                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                Log.d(TAG, "Ringer mode changed from " + ringerMode + " to NORMAL");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Cannot override silent mode: " + e.getMessage());
        }
    }

    private void showDNDWarning() {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("⚠️ Do Not Disturb Active");
            builder.setMessage("Please allow DQRA to bypass Do Not Disturb for emergency alerts.");
            builder.setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            });
            builder.setNegativeButton("Later", null);
            builder.show();
        });
    }

    private void showVolumeWarningNotification() {
        runOnUiThread(() -> {
            Toast.makeText(AdminDashboard.this,
                    "Emergency Alert! Please check device volume",
                    Toast.LENGTH_LONG).show();
        });

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "EmergencyAlerts")
                .setSmallIcon(R.drawable.baseline_warning_24)
                .setContentTitle("EMERGENCY ALERT - CHECK VOLUME")
                .setContentText("Device may be on silent or low volume. Please increase volume!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_VOLUME_WARNING, builder.build());
    }

    private void listenForWebsiteNotifications() {
        websiteNotificationListener = db.collection("website_notifications")
                .whereEqualTo("municipality", TARGET_MUNICIPALITY)
                .whereEqualTo("status", "unread")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Website notification listen failed: " + error.getMessage());
                        return;
                    }

                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "New website notifications: " + queryDocumentSnapshots.size());

                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            handleWebsiteNotification(document);
                            document.getReference().update("status", "read");
                        }
                    }
                });
    }

    private void handleWebsiteNotification(QueryDocumentSnapshot document) {
        String url = "https://fsis.e-bfp.com/";

        showNotification("Website Access", "A user has accessed the website for inquiries", NOTIFICATION_ID_WEBSITE);

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void saveCurrentEmergencyToPrefs() {
        if (currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(CURRENT_EMERGENCY_KEY, currentEmergencyId);
            editor.putFloat(CURRENT_EMERGENCY_LAT_KEY, (float) emergencyLatitude);
            editor.putFloat(CURRENT_EMERGENCY_LNG_KEY, (float) emergencyLongitude);
            editor.putString(CURRENT_EMERGENCY_PHONE_KEY, Phone);
            editor.putString(CURRENT_EMERGENCY_REPORTED_KEY, Reported);
            editor.putString(CURRENT_EMERGENCY_MUNICIPALITY_KEY, emergencyMunicipality);
            editor.putString(CURRENT_EMERGENCY_STATUS_KEY, currentEmergencyStatus);
            editor.putBoolean("current_emergency_has_image", currentEmergencyHasImage);
            editor.apply();
            Log.d(TAG, "Saved current emergency to preferences: " + currentEmergencyId);
        }
    }

    private void loadCurrentEmergencyFromPrefs() {
        currentEmergencyId = sharedPreferences.getString(CURRENT_EMERGENCY_KEY, "");
        emergencyLatitude = sharedPreferences.getFloat(CURRENT_EMERGENCY_LAT_KEY, 0);
        emergencyLongitude = sharedPreferences.getFloat(CURRENT_EMERGENCY_LNG_KEY, 0);
        Phone = sharedPreferences.getString(CURRENT_EMERGENCY_PHONE_KEY, "");
        Reported = sharedPreferences.getString(CURRENT_EMERGENCY_REPORTED_KEY, "");
        emergencyMunicipality = sharedPreferences.getString(CURRENT_EMERGENCY_MUNICIPALITY_KEY, "");
        currentEmergencyStatus = sharedPreferences.getString(CURRENT_EMERGENCY_STATUS_KEY, STATUS_PENDING);
        currentEmergencyHasImage = sharedPreferences.getBoolean("current_emergency_has_image", false);

        Log.d(TAG, "Loaded emergency from preferences: " + currentEmergencyId + " with status: " + currentEmergencyStatus);

        if (currentEmergencyId != null && !currentEmergencyId.isEmpty() &&
                !currentEmergencyId.startsWith("messenger_")) {
            fetchEmergencyImageData(currentEmergencyId);
        }
    }

    private void clearCurrentEmergencyFromPrefs() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(CURRENT_EMERGENCY_KEY);
        editor.remove(CURRENT_EMERGENCY_LAT_KEY);
        editor.remove(CURRENT_EMERGENCY_LNG_KEY);
        editor.remove(CURRENT_EMERGENCY_PHONE_KEY);
        editor.remove(CURRENT_EMERGENCY_REPORTED_KEY);
        editor.remove(CURRENT_EMERGENCY_MUNICIPALITY_KEY);
        editor.remove(CURRENT_EMERGENCY_STATUS_KEY);
        editor.remove("current_emergency_has_image");
        editor.apply();
        Log.d(TAG, "Cleared current emergency from preferences");
    }

    private String getStatusDisplayText(String status) {
        switch (status) {
            case STATUS_CONFIRMED:
                return "EMERGENCY CONFIRMED";
            case STATUS_REJECTED:
                return "EMERGENCY REJECTED";
            case STATUS_RESOLVED:
                return "✓ EMERGENCY RESOLVED";
            case STATUS_PENDING:
            default:
                return "EMERGENCY ALERT";
        }
    }

    private void logoutUser() {
        stopEmergencyAlerts();

        if (emergencyLocationListener != null) {
            emergencyLocationListener.remove();
        }

        clearCurrentEmergencyFromPrefs();

        auth.signOut();
        startActivity(new Intent(this, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void setStatisticsCardsEnabled(boolean enabled) {
        LinearLayout layoutPending = findViewById(R.id.layoutPending);
        LinearLayout layoutActive = findViewById(R.id.layoutActive);
        LinearLayout layoutResolved = findViewById(R.id.layoutResolved);

        if (layoutPending != null) {
            layoutPending.setEnabled(enabled);
            layoutPending.setAlpha(enabled ? 1.0f : 0.5f);
        }

        if (layoutActive != null) {
            layoutActive.setEnabled(enabled);
            layoutActive.setAlpha(enabled ? 1.0f : 0.5f);
        }

        if (layoutResolved != null) {
            layoutResolved.setEnabled(enabled);
            layoutResolved.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    private void initializeFirebase() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        emergencyLocationRef = db.collection("emergency_locations");
    }

    @Override
    protected Class<?> getHomeActivityClass() {
        return AdminDashboard.class;
    }

    @Override
    protected Class<?> getProfileActivityClass() {
        return AdminProfileActivity.class;
    }

    private void observeViewModel() {
        viewModel.getDashboardState().observe(this, this::renderDashboardState);
        viewModel.getEmergencyState().observe(this, this::renderEmergencyStreamState);
        viewModel.getStatsState().observe(this, this::renderStats);
        viewModel.getActionError().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderDashboardState(AdminDashboardUiState state) {
        if (state == null) {
            return;
        }

        if (state.getStatus() == AdminDashboardUiState.Status.READY) {
            adminMunicipality = state.getMunicipality();
            if (tvAdminMunicipality != null) {
                tvAdminMunicipality.setText(adminMunicipality);
            }
            Log.d(TAG, "Admin municipality: " + adminMunicipality);
        } else if (state.getStatus() == AdminDashboardUiState.Status.ERROR) {
            Log.e(TAG, "Admin setup failed: " + state.getMessage());
            Toast.makeText(this, state.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void renderEmergencyStreamState(AdminEmergencyStreamState state) {
        if (state == null) {
            return;
        }

        switch (state.getStatus()) {
            case EMPTY:
                Log.d(TAG, "No emergency documents found for municipality: " + adminMunicipality);
                if (currentEmergencyId == null || !currentEmergencyId.startsWith("messenger_")) {
                    clearEmergencyUI();
                }
                break;
            case EMERGENCY:
                processEmergency(state.getEmergency());
                break;
            case ERROR:
                Log.e(TAG, "Emergency listener failed: " + state.getMessage());
                break;
            default:
                break;
        }
    }

    private void renderStats(AdminEmergencyStats stats) {
        if (stats == null) {
            return;
        }

        if (tvPendingCount != null) tvPendingCount.setText(String.valueOf(stats.getPendingCount()));
        if (tvActiveCount != null) tvActiveCount.setText(String.valueOf(stats.getActiveCount()));
        if (tvResolvedCount != null) tvResolvedCount.setText(String.valueOf(stats.getResolvedCount()));

        Log.d(TAG, "Statistics updated - Pending: " + stats.getPendingCount()
                + ", Active: " + stats.getActiveCount()
                + ", Resolved (all): " + stats.getResolvedCount());
    }

    private void loadAlertedEmergencies() {
        sharedPreferences = getSharedPreferences(EMERGENCY_PREFS, Context.MODE_PRIVATE);
        alertedEmergencies = new HashSet<>(sharedPreferences.getStringSet(ALERTED_EMERGENCIES_KEY, new HashSet<>()));
        Log.d(TAG, "Loaded alerted emergencies: " + alertedEmergencies.size());
    }

    private void markEmergencyAsAlerted(String emergencyId) {
        if (emergencyId != null && !emergencyId.isEmpty()) {
            alertedEmergencies.add(emergencyId);
            sharedPreferences.edit()
                    .putStringSet(ALERTED_EMERGENCIES_KEY, alertedEmergencies)
                    .apply();
            Log.d(TAG, "Marked emergency as alerted: " + emergencyId);
        }
    }

    private boolean isEmergencyAlreadyAlerted(String emergencyId) {
        return emergencyId != null && alertedEmergencies.contains(emergencyId);
    }

    private void clearOldEmergencies() {
        if (alertedEmergencies.size() > 100) {
            alertedEmergencies.clear();
            sharedPreferences.edit()
                    .putStringSet(ALERTED_EMERGENCIES_KEY, alertedEmergencies)
                    .apply();
            Log.d(TAG, "Cleared alerted emergencies list due to size limit");
        }
    }

    private void fetchAdminMunicipality() {
        viewModel.setCurrentEmergencyId(currentEmergencyId);
        viewModel.loadAdminMunicipality();
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String packageName = getPackageName();

            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                boolean hasAskedBefore = prefs.getBoolean("battery_optimization_asked", false);

                if (!hasAskedBefore) {
                    new AlertDialog.Builder(this)
                            .setTitle("Background Permission Required")
                            .setMessage("DQRA needs to run in the background to receive emergency alerts even when the app is closed.\n\nPlease allow the app to run in the background.")
                            .setPositiveButton("Allow", (dialog, which) -> {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + packageName));
                                startActivity(intent);
                                prefs.edit().putBoolean("battery_optimization_asked", true).apply();
                            })
                            .setNegativeButton("Later", (dialog, which) -> {
                                prefs.edit().putBoolean("battery_optimization_asked", true).apply();
                                Toast.makeText(this,
                                        "You can enable this later in Settings",
                                        Toast.LENGTH_LONG).show();
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    Log.d(TAG, "Battery optimization already requested before");
                }
            } else {
                Log.d(TAG, "App is already ignoring battery optimizations");
            }
        }
    }

    private void startFirestoreService() {
        Intent serviceIntent = new Intent(this, FirestoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void initializeUI() {
        btnViewMap = findViewById(R.id.btnViewMap);
        btnLogout = findViewById(R.id.btnLogout);
        btnLogs = findViewById(R.id.btnLogs);
        btnStopSound = findViewById(R.id.btnStopSound);
        tvEmergencyAlert = findViewById(R.id.tvEmergencyAlert);
        btnCallSender = findViewById(R.id.btnCallSender);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnReject = findViewById(R.id.btnReject);
        btnViewImage = findViewById(R.id.btnViewImage);
        btnShareMessenger = findViewById(R.id.btnShareMessenger); // ✅ NEW

        Button btnComplete = findViewById(R.id.btnComplete);

        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvActiveCount = findViewById(R.id.tvActiveCount);
        tvResolvedCount = findViewById(R.id.tvResolvedCount);
        tvStatusIcon = findViewById(R.id.tvStatusIcon);
        tvEmergencyTime = findViewById(R.id.tvEmergencyTime);
        tvSenderName = findViewById(R.id.tvSenderName);
        tvEmergencyActionsHeader = findViewById(R.id.tvEmergencyActionsHeader);
        tvInfoHeader = findViewById(R.id.tvInfoHeader);
        tvAdminMunicipality = findViewById(R.id.tvAdminMunicipality);
        cardEmergencyStatus = findViewById(R.id.cardEmergencyStatus);

        setupStatisticsClickListeners();

        btnStopSound.setOnClickListener(v -> {
            Log.d(TAG, "Stop Sound button clicked");

            stopEmergencyAlerts();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAlertPlaying()) {
                    Log.w(TAG, "Sound still playing after 300ms - forcing kill");
                    stopEmergencyAlerts();
                }
            }, 300);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAlertPlaying()) {
                    Log.e(TAG, "Sound STILL playing after 500ms - nuclear option");
                    forceKillAllSounds();
                }
            }, 500);

            btnStopSound.setVisibility(View.GONE);
            Toast.makeText(this, "🔕 Emergency alert stopped", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> logoutUser());
        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> redirectToFilteredLogs("all"));
        }
        btnViewMap.setOnClickListener(v -> openGoogleMaps());
        btnCallSender.setOnClickListener(v -> callOperation());
        btnViewImage.setOnClickListener(v -> showEmergencyImage());

        // ✅ NEW: Share via Messenger with debounce guard to prevent double-send
        btnShareMessenger.setOnClickListener(v -> {
            if (isShareInProgress) {
                Log.d(TAG, "Share already in progress, ignoring duplicate tap");
                return;
            }
            isShareInProgress = true;
            btnShareMessenger.setEnabled(false);
            shareLocationViaMessenger();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isShareInProgress = false;
                if (btnShareMessenger != null) {
                    btnShareMessenger.setEnabled(true);
                }
            }, 2000);
        });

        // Initial visibility — all hidden
        btnViewMap.setVisibility(View.GONE);
        btnStopSound.setVisibility(View.GONE);
        btnCallSender.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        btnReject.setVisibility(View.GONE);
        btnViewImage.setVisibility(View.GONE);
        btnComplete.setVisibility(View.GONE);
        btnShareMessenger.setVisibility(View.GONE); // ✅ NEW

        btnConfirm.setOnClickListener(v -> {
            if (currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
                confirmEmergency(currentEmergencyId);
            }
        });

        btnReject.setOnClickListener(v -> {
            if (currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
                rejectEmergency(currentEmergencyId);
            }
        });

        btnComplete.setOnClickListener(v -> {
            if (currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
                showResolveConfirmationDialog(currentEmergencyId);
            }
        });
    }

    // ✅ NEW: Plain string concatenation prevents Messenger from duplicating the message body
    private void shareLocationViaMessenger() {
        if (emergencyLatitude == 0 && emergencyLongitude == 0) {
            Toast.makeText(this, "No emergency location available", Toast.LENGTH_SHORT).show();
            isShareInProgress = false;
            if (btnShareMessenger != null) btnShareMessenger.setEnabled(true);
            return;
        }

        String mapsLink = "https://www.google.com/maps?q="
                + emergencyLatitude + "," + emergencyLongitude;

        String reportedByText = (Reported != null && !Reported.isEmpty()) ? Reported : "Unknown";
        String municipalityText = (emergencyMunicipality != null && !emergencyMunicipality.isEmpty())
                ? emergencyMunicipality : "Unknown";

        String message = "🚨 EMERGENCY DISPATCH\n"
                + "Reporter: " + reportedByText + "\n"
                + "Location: " + municipalityText + "\n\n"
                + "📍 Open in Google Maps:\n"
                + mapsLink + "\n\n"
                + "⚠️ Respond immediately.";

        Intent messengerIntent = new Intent(Intent.ACTION_SEND);
        messengerIntent.setType("text/plain");
        messengerIntent.setPackage("com.facebook.orca");
        messengerIntent.putExtra(Intent.EXTRA_TEXT, message);

        try {
            startActivity(messengerIntent);
            Log.d(TAG, "Opened Messenger with emergency location: " + mapsLink);
        } catch (android.content.ActivityNotFoundException e) {
            Log.w(TAG, "Messenger not installed, falling back to generic share");
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, message);
            startActivity(Intent.createChooser(shareIntent, "Share Emergency Location via..."));
        }
    }

    private void setupStatisticsClickListeners() {
        LinearLayout layoutPending = findViewById(R.id.layoutPending);
        LinearLayout layoutActive = findViewById(R.id.layoutActive);
        LinearLayout layoutResolved = findViewById(R.id.layoutResolved);

        layoutPending.setOnClickListener(v -> {
            if (!v.isEnabled()) {
                showPendingEmergencyWarning();
                return;
            }
            redirectToFilteredLogs("pending");
        });

        layoutActive.setOnClickListener(v -> {
            if (!v.isEnabled()) {
                showPendingEmergencyWarning();
                return;
            }
            redirectToFilteredLogs("confirmed");
        });

        layoutResolved.setOnClickListener(v -> {
            if (!v.isEnabled()) {
                showPendingEmergencyWarning();
                return;
            }
            redirectToFilteredLogs("resolved");
        });
    }

    private void showPendingEmergencyWarning() {
        Toast.makeText(this,
                "⚠️ Please respond to the current emergency before viewing logs",
                Toast.LENGTH_LONG).show();
    }

    private void redirectToFilteredLogs(String statusFilter) {
        Intent intent = new Intent(this, EmergencyLogsAdapter.class);
        intent.putExtra("STATUS_FILTER", statusFilter);
        startActivity(intent);
    }

    private void showResolveConfirmationDialog(String emergencyId) {
        new AlertDialog.Builder(this)
                .setTitle("Mark as Resolved")
                .setMessage("Are you sure you want to mark this emergency as resolved?\n\nThis action confirms that the situation has been handled successfully.")
                .setPositiveButton("Yes, Resolved", (dialog, which) -> {
                    resolveEmergency(emergencyId);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.baseline_warning_24)
                .show();
    }

    private void resolveEmergency(String emergencyId) {
        if (emergencyId == null || emergencyId.isEmpty()) {
            Toast.makeText(this, "Invalid emergency ID", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> {
            Button btnComplete = findViewById(R.id.btnComplete);
            if (btnComplete != null) {
                btnComplete.setEnabled(false);
                btnComplete.setText("Resolving...");
            }
        });

        if (emergencyId.startsWith("messenger_")) {
            currentEmergencyStatus = STATUS_RESOLVED;
            saveCurrentEmergencyToPrefs();

            runOnUiThread(() -> {
                Toast.makeText(this, "✓ Messenger emergency marked as resolved", Toast.LENGTH_SHORT).show();
                updateEmergencyUI();
                updateEmergencyStatistics();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    clearCurrentEmergency();
                }, 2000);
            });
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", STATUS_RESOLVED);
        updates.put("resolvedAt", FieldValue.serverTimestamp());
        updates.put("resolvedBy", auth.getCurrentUser().getUid());

        db.collection("emergency_locations")
                .document(emergencyId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    currentEmergencyStatus = STATUS_RESOLVED;
                    saveCurrentEmergencyToPrefs();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "✓ Emergency marked as resolved", Toast.LENGTH_SHORT).show();

                        tvEmergencyAlert.setText(String.format(Locale.getDefault(),
                                "✓ EMERGENCY RESOLVED\n\n" +
                                        "Reported By: %s\n" +
                                        "Contact: %s\n" +
                                        "Municipality: %s\n" +
                                        "Status: RESOLVED",
                                Reported, Phone, emergencyMunicipality));

                        Button btnComplete = findViewById(R.id.btnComplete);
                        if (btnComplete != null) {
                            btnComplete.setVisibility(View.GONE);
                        }
                        btnConfirm.setVisibility(View.GONE);
                        btnReject.setVisibility(View.GONE);
                        btnStopSound.setVisibility(View.GONE);
                        btnShareMessenger.setVisibility(View.GONE); // ✅ NEW

                        updateEmergencyStatistics();

                        showNotification("Emergency Resolved",
                                "Emergency from " + Reported + " has been marked as resolved",
                                NOTIFICATION_ID_EMERGENCY_RESOLVED);

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            clearCurrentEmergency();
                        }, 3000);
                    });

                    Log.d(TAG, "Emergency resolved successfully: " + emergencyId);
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to resolve: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                        Button btnComplete = findViewById(R.id.btnComplete);
                        if (btnComplete != null) {
                            btnComplete.setEnabled(true);
                            btnComplete.setText("✓ Mark as Resolved");
                        }
                    });

                    Log.e(TAG, "Error resolving emergency: " + e.getMessage());
                });
    }

    private void updateEmergencyStatistics() {
        if (adminMunicipality == null || adminMunicipality.isEmpty()) {
            Log.w(TAG, "Cannot update statistics: adminMunicipality is null or empty");
            runOnUiThread(() -> {
                if (tvPendingCount != null) tvPendingCount.setText("0");
                if (tvActiveCount != null) tvActiveCount.setText("0");
                if (tvResolvedCount != null) tvResolvedCount.setText("0");
            });
            return;
        }

        viewModel.refreshStats();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionFlowActive &&
                (requestCode == PERMISSION_REQUEST_LOCATION || requestCode == PERMISSION_REQUEST_NOTIFICATIONS)) {
            requestNextPermissionStep();
        }

        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (Phone != null && !Phone.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + Phone));
                startActivity(intent);
            }
        }

        if ((requestCode == 103 || requestCode == 104) &&
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio/notification permission granted");
            if (currentEmergencyId != null && !currentEmergencyId.isEmpty() &&
                    STATUS_PENDING.equals(currentEmergencyStatus)) {
                ensureEmergencyVolume();
            }
        }
    }

    private void callOperation() {
        if (Phone != null && !Phone.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + Phone));

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
            } else {
                startActivity(intent);
            }
        } else {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private void initializeLocationClient() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            if (intent.getBooleanExtra("EMERGENCY_NOTIFICATION", false)) {
                intent.removeExtra("EMERGENCY_NOTIFICATION");
                Toast.makeText(this, "Emergency notification received!", Toast.LENGTH_LONG).show();
            }

            if (intent.hasExtra("lat") && intent.hasExtra("lon")) {
                String latStr = intent.getStringExtra("lat");
                String lonStr = intent.getStringExtra("lon");
                String senderName = intent.getStringExtra("senderName");
                String reportedBy = intent.getStringExtra("reportedBy");
                String phoneNumber = intent.getStringExtra("phoneNumber");
                String messageText = intent.getStringExtra("messageText");

                if (latStr != null && lonStr != null) {
                    try {
                        stopEmergencyAlerts();

                        currentEmergencyId = "messenger_" + System.currentTimeMillis();
                        emergencyLatitude = Double.parseDouble(latStr);
                        emergencyLongitude = Double.parseDouble(lonStr);
                        Phone = firstNonEmpty(phoneNumber, extractMessengerField(messageText, "Phone"), "Via Messenger");
                        Reported = firstNonEmpty(reportedBy, extractMessengerField(messageText, "Name"), senderName, "Messenger User");
                        emergencyMunicipality = adminMunicipality != null ? adminMunicipality : "Unknown";
                        currentEmergencyStatus = STATUS_PENDING;

                        currentEmergencyHasImage = false;
                        currentEmergencyImageBase64 = "";

                        saveCurrentEmergencyToPrefs();
                        markEmergencyAsAlerted(currentEmergencyId);

                        Log.d(TAG, "Messenger emergency loaded from intent: " + currentEmergencyId +
                                " from: " + Reported);
                        if (messageText != null && !messageText.isEmpty()) {
                            Log.d(TAG, "Message content: " + messageText);
                        }

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            showEmergencyAlert();
                        }, 500);

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid coordinates in intent: " + latStr + ", " + lonStr, e);
                    }
                }
            }
        }
    }

    private String extractMessengerField(String message, String label) {
        if (message == null) {
            return "";
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?im)^\\s*" + java.util.regex.Pattern.quote(label) + "\\s*:\\s*(.+?)\\s*$");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private void fetchEmergencyImageData(String emergencyId) {
        db.collection("emergency_locations")
                .document(emergencyId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean hasImage = documentSnapshot.getBoolean("hasImage");
                        String imageBase64 = documentSnapshot.getString("imageBase64");

                        currentEmergencyHasImage = hasImage != null ? hasImage : false;
                        currentEmergencyImageBase64 = imageBase64 != null ? imageBase64 : "";

                        runOnUiThread(() -> {
                            btnViewImage.setVisibility(currentEmergencyHasImage ? View.VISIBLE : View.GONE);
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching image data: " + e.getMessage());
                });
    }

    private void showEmergencyImage() {
        if (!currentEmergencyHasImage || currentEmergencyImageBase64.isEmpty()) {
            Toast.makeText(this, "No image available", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, FullScreenImageActivity.class);
        intent.putExtra("image_base64", currentEmergencyImageBase64);
        startActivity(intent);
    }

    private void listenForEmergencyLocationChanges() {
        if (adminMunicipality == null || adminMunicipality.isEmpty()) {
            Log.e(TAG, "Cannot listen for emergencies: admin municipality is empty");
            return;
        }

        Log.d(TAG, "Starting ViewModel listener for emergencies in: " + adminMunicipality);
        viewModel.setCurrentEmergencyId(currentEmergencyId);
        viewModel.startEmergencyListener();
    }

    private void listenForEmergencyLocationChangesLegacy() {
        if (adminMunicipality == null || adminMunicipality.isEmpty()) {
            Log.e(TAG, "Cannot listen for emergencies: admin municipality is empty");
            return;
        }

        Log.d(TAG, "Starting listener for emergencies in: " + adminMunicipality);

        if (emergencyLocationListener != null) {
            emergencyLocationListener.remove();
        }

        emergencyLocationListener = emergencyLocationRef
                .whereEqualTo("municipality", adminMunicipality)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error);
                        return;
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No emergency documents found for municipality: " + adminMunicipality);
                        if (currentEmergencyId == null || !currentEmergencyId.startsWith("messenger_")) {
                            clearEmergencyUI();
                        } else {
                            Log.d(TAG, "Keeping Messenger emergency despite no Firestore documents");
                        }
                        return;
                    }

                    boolean currentEmergencyStillExists = false;

                    QueryDocumentSnapshot mostRecentPendingEmergency = null;
                    QueryDocumentSnapshot currentEmergencyDoc = null;
                    long mostRecentTimestamp = 0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            String status = document.getString("status");
                            String emergencyId = document.getId();

                            Object timestampObj = document.get("timestamp");
                            long timestamp = 0;

                            if (timestampObj instanceof Long) {
                                timestamp = (Long) timestampObj;
                            } else if (timestampObj instanceof com.google.firebase.Timestamp) {
                                timestamp = ((com.google.firebase.Timestamp) timestampObj).toDate().getTime();
                            }

                            // ✅ Skip old emergencies from dashboard but keep in DB for history logs
                            boolean isOldEmergency = timestamp > 0 &&
                                    System.currentTimeMillis() - timestamp > 10 * 60 * 60 * 1000;

                            if (isOldEmergency && !emergencyId.equals(currentEmergencyId)) {
                                Log.d(TAG, "Skipping old emergency from dashboard (>10h): " + emergencyId);
                                continue;
                            }

                            if (STATUS_PENDING.equals(status)) {
                                if (timestamp > mostRecentTimestamp) {
                                    mostRecentTimestamp = timestamp;
                                    mostRecentPendingEmergency = document;
                                }
                            }

                            if (emergencyId.equals(currentEmergencyId)) {
                                currentEmergencyDoc = document;
                                currentEmergencyStillExists = true;
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing emergency document: " + e.getMessage(), e);
                        }
                    }

                    if (!currentEmergencyStillExists && currentEmergencyId != null && !currentEmergencyId.isEmpty()) {
                        if (!currentEmergencyId.startsWith("messenger_")) {
                            Log.d(TAG, "Current Firestore emergency no longer exists: " + currentEmergencyId);
                            clearCurrentEmergency();
                            return;
                        } else {
                            Log.d(TAG, "Keeping Messenger emergency: " + currentEmergencyId);
                        }
                    }

                    if (currentEmergencyDoc != null) {
                        String newStatus = currentEmergencyDoc.getString("status");
                        if (!newStatus.equals(currentEmergencyStatus)) {
                            currentEmergencyStatus = newStatus;
                            Log.d(TAG, "Emergency status updated: " + currentEmergencyId + " -> " + newStatus);
                            restoreEmergencyUI();
                            saveCurrentEmergencyToPrefs();
                        }
                    }

                    if (mostRecentPendingEmergency != null) {
                        if (currentEmergencyId != null && currentEmergencyId.startsWith("messenger_") &&
                                STATUS_PENDING.equals(currentEmergencyStatus)) {
                            Log.d(TAG, "Skipping Firestore emergency - Messenger emergency is active");
                        } else {
                            processEmergencyDocument(mostRecentPendingEmergency);
                        }
                    }
                });
    }

    private void clearCurrentEmergency() {
        if (currentEmergencyId != null && currentEmergencyId.startsWith("messenger_") &&
                (STATUS_CONFIRMED.equals(currentEmergencyStatus) || STATUS_REJECTED.equals(currentEmergencyStatus))) {
            Log.d(TAG, "Keeping confirmed/rejected Messenger emergency: " + currentEmergencyId);
            return;
        }

        runOnUiThread(() -> {
            stopEmergencyAlerts();

            if (imageUpdateListener != null) {
                imageUpdateListener.remove();
                imageUpdateListener = null;
            }

            clearEmergencyUI();

            currentEmergencyId = "";
            emergencyLatitude = 0;
            emergencyLongitude = 0;
            Phone = "";
            Reported = "";
            emergencyMunicipality = "";
            currentEmergencyStatus = STATUS_PENDING;
            currentEmergencyImageBase64 = "";
            currentEmergencyHasImage = false;

            clearCurrentEmergencyFromPrefs();
            setStatisticsCardsEnabled(true);

            Log.d(TAG, "Current emergency cleared from UI and data");
        });
    }

    private void clearEmergencyUI() {
        runOnUiThread(() -> {
            tvEmergencyAlert.setText("No active emergencies");
            btnViewMap.setVisibility(View.GONE);
            btnCallSender.setVisibility(View.GONE);
            btnConfirm.setVisibility(View.GONE);
            btnReject.setVisibility(View.GONE);
            btnStopSound.setVisibility(View.GONE);
            btnViewImage.setVisibility(View.GONE);
            btnShareMessenger.setVisibility(View.GONE); // ✅ NEW

            Log.d(TAG, "Emergency UI cleared");
        });
    }

    private void processEmergency(AdminEmergency emergency) {
        if (emergency == null) {
            return;
        }

        try {
            String emergencyId = emergency.getId();
            String municipality = emergency.getMunicipality();

            if (municipality != null && municipality.equals(adminMunicipality)) {
                boolean isNewEmergency = !emergencyId.equals(currentEmergencyId);

                currentEmergencyId = emergencyId;
                viewModel.setCurrentEmergencyId(currentEmergencyId);
                emergencyLatitude = emergency.getLatitude();
                emergencyLongitude = emergency.getLongitude();
                Phone = emergency.getPhoneNumber();
                Reported = emergency.getReportedBy() != null ? emergency.getReportedBy() : "Unknown";
                emergencyMunicipality = municipality;
                currentEmergencyStatus = emergency.getStatus() != null ? emergency.getStatus() : STATUS_PENDING;
                currentEmergencyHasImage = emergency.hasImage();
                currentEmergencyImageBase64 = emergency.getImageBase64() != null ? emergency.getImageBase64() : "";

                Log.d(TAG, "Processing emergency ID: " + emergencyId + ", isNew: " + isNewEmergency +
                        ", status: " + currentEmergencyStatus + ", hasImage: " + currentEmergencyHasImage);

                updateAdminLocation();

                if (isNewEmergency && STATUS_PENDING.equals(currentEmergencyStatus)
                        && !isEmergencyAlreadyAlerted(emergencyId)) {
                    Log.d(TAG, "New pending emergency found in " + municipality + ", ID: " + emergencyId);
                    markEmergencyAsAlerted(emergencyId);
                    showEmergencyAlert();
                } else {
                    Log.d(TAG, "Updating UI for existing/processed emergency: " + emergencyId);
                    updateEmergencyUI();
                }

                saveCurrentEmergencyToPrefs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing emergency: " + e.getMessage(), e);
        }
    }

    private void processEmergencyDocument(QueryDocumentSnapshot document) {
        try {
            Double lat = document.getDouble("latitude");
            Double lng = document.getDouble("longitude");
            String municipality = document.getString("municipality");
            String phonenumber = document.getString("phoneNumber");
            String reportedby = document.getString("reportedBy");
            String status = document.getString("status");
            String emergencyId = document.getId();

            Boolean hasImage = document.getBoolean("hasImage");
            String imageBase64 = document.getString("imageBase64");

            if (lat != null && lng != null && municipality != null &&
                    municipality.equals(adminMunicipality)) {

                boolean isNewEmergency = !emergencyId.equals(currentEmergencyId);

                currentEmergencyId = emergencyId;
                emergencyLatitude = lat;
                emergencyLongitude = lng;
                Phone = phonenumber;
                Reported = reportedby != null ? reportedby : "Unknown";
                emergencyMunicipality = municipality;
                currentEmergencyStatus = status != null ? status : STATUS_PENDING;
                currentEmergencyHasImage = hasImage != null ? hasImage : false;
                currentEmergencyImageBase64 = imageBase64 != null ? imageBase64 : "";

                Log.d(TAG, "Processing emergency ID: " + emergencyId + ", isNew: " + isNewEmergency +
                        ", status: " + currentEmergencyStatus + ", hasImage: " + currentEmergencyHasImage);

                updateAdminLocation();

                if (isNewEmergency && STATUS_PENDING.equals(currentEmergencyStatus) && !isEmergencyAlreadyAlerted(emergencyId)) {
                    Log.d(TAG, "New pending emergency found in " + municipality + ", ID: " + emergencyId);
                    markEmergencyAsAlerted(emergencyId);
                    showEmergencyAlert();
                } else {
                    Log.d(TAG, "Updating UI for existing/processed emergency: " + emergencyId);
                    updateEmergencyUI();
                }

                saveCurrentEmergencyToPrefs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing emergency document: " + e.getMessage(), e);
        }
    }

    private void updateAdminLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        viewModel.updateAdminLocation(location);
                        Log.d(TAG, "Admin location update requested");
                    }
                });
    }

    private void updateEmergencyUI() {
        runOnUiThread(() -> {
            String statusText = getStatusDisplayText(currentEmergencyStatus);
            String imageText = currentEmergencyHasImage ? "\n📷 Image Available" : "";
            String sourceText = currentEmergencyId.startsWith("messenger_") ? "\n📱 From Messenger" : "";

            tvEmergencyAlert.setText(String.format(Locale.getDefault(),
                    "%s\n\n" +
                            "Reported By: %s\n" +
                            "Contact: %s\n" +
                            "Municipality: %s%s%s",
                    statusText, Reported, Phone, emergencyMunicipality, imageText, sourceText));

            btnViewMap.setVisibility(View.VISIBLE);
            btnCallSender.setVisibility(View.VISIBLE);
            btnShareMessenger.setVisibility(View.VISIBLE); // ✅ NEW
            btnViewImage.setVisibility(currentEmergencyHasImage ? View.VISIBLE : View.GONE);

            Button btnComplete = findViewById(R.id.btnComplete);

            if (STATUS_PENDING.equals(currentEmergencyStatus)) {
                btnConfirm.setVisibility(View.VISIBLE);
                btnReject.setVisibility(View.VISIBLE);
                btnStopSound.setVisibility(View.VISIBLE);
                if (btnComplete != null) btnComplete.setVisibility(View.GONE);
                playEmergencyAlerts();
            } else if (STATUS_CONFIRMED.equals(currentEmergencyStatus)) {
                stopEmergencyAlerts();

                btnConfirm.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnStopSound.setVisibility(View.GONE);

                if (btnComplete != null) {
                    btnComplete.setVisibility(View.VISIBLE);
                    btnComplete.setEnabled(true);
                    btnComplete.setText("✓ Mark as Resolved");
                }
            } else {
                stopEmergencyAlerts();

                btnConfirm.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnStopSound.setVisibility(View.GONE);
                if (btnComplete != null) btnComplete.setVisibility(View.GONE);
            }
        });
    }

    private void showEmergencyAlert() {
        runOnUiThread(() -> {
            String imageText = currentEmergencyHasImage ? "\n📷 Image Available" : "";
            String sourceText = currentEmergencyId.startsWith("messenger_") ? "\n📱 From Messenger" : "";

            tvEmergencyAlert.setText(String.format(Locale.getDefault(),
                    "🚨 NEW EMERGENCY ALERT!\n\n" +
                            "Reported By: %s\n" +
                            "Contact: %s\n" +
                            "Municipality: %s%s%s",
                    Reported, Phone, emergencyMunicipality, imageText, sourceText));

            btnViewMap.setVisibility(View.VISIBLE);
            btnCallSender.setVisibility(View.VISIBLE);
            btnShareMessenger.setVisibility(View.VISIBLE); // ✅ NEW
            btnConfirm.setVisibility(View.VISIBLE);
            btnReject.setVisibility(View.VISIBLE);
            btnStopSound.setVisibility(View.VISIBLE);

            btnViewImage.setVisibility(currentEmergencyHasImage ? View.VISIBLE : View.GONE);

            if (tvEmergencyActionsHeader != null) {
                tvEmergencyActionsHeader.setVisibility(View.VISIBLE);
            }

            playEmergencyAlerts();
            showNotification("Emergency Alert", "New emergency from " + Reported, NOTIFICATION_ID_EMERGENCY_ALERT);

            updateEmergencyStatistics();
            setStatisticsCardsEnabled(false);
        });
    }

    private void confirmEmergency(String emergencyId) {
        if (emergencyId == null || emergencyId.isEmpty()) {
            Toast.makeText(this, "Invalid emergency ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String previousStatus = currentEmergencyStatus;
        currentEmergencyStatus = STATUS_CONFIRMED;

        runOnUiThread(() -> {
            stopEmergencyAlerts();
            new Handler(Looper.getMainLooper()).postDelayed(() -> stopEmergencyAlerts(), 100);
            new Handler(Looper.getMainLooper()).postDelayed(() -> stopEmergencyAlerts(), 300);

            clearEmergencyNotifications(); // ✅ NEW

            btnConfirm.setVisibility(View.GONE);
            btnReject.setVisibility(View.GONE);
            btnStopSound.setVisibility(View.GONE);

            if (tvEmergencyActionsHeader != null) {
                tvEmergencyActionsHeader.setVisibility(View.GONE);
            }

            setStatisticsCardsEnabled(true);
        });

        if (emergencyId.startsWith("messenger_")) {
            saveCurrentEmergencyToPrefs();
            Toast.makeText(this, "Messenger emergency confirmed", Toast.LENGTH_SHORT).show();
            updateEmergencyUI();
            updateEmergencyStatistics();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", STATUS_CONFIRMED);
        updates.put("confirmedAt", FieldValue.serverTimestamp());

        db.collection("emergency_locations")
                .document(emergencyId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    saveCurrentEmergencyToPrefs();
                    Toast.makeText(this, "Emergency confirmed", Toast.LENGTH_SHORT).show();

                    startImageUpdateListener(emergencyId);
                    updateEmergencyUI();
                    updateEmergencyStatistics();
                })
                .addOnFailureListener(e -> {
                    currentEmergencyStatus = previousStatus;

                    Toast.makeText(this, "Failed to confirm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    runOnUiThread(() -> {
                        btnConfirm.setVisibility(View.VISIBLE);
                        btnReject.setVisibility(View.VISIBLE);
                        btnStopSound.setVisibility(View.VISIBLE);
                        setStatisticsCardsEnabled(false);

                        if (STATUS_PENDING.equals(currentEmergencyStatus)) {
                            playEmergencyAlerts();
                        }
                    });
                });
    }

    private void rejectEmergency(String emergencyId) {
        if (emergencyId == null || emergencyId.isEmpty()) {
            Toast.makeText(this, "Invalid emergency ID", Toast.LENGTH_SHORT).show();
            return;
        }

        String previousStatus = currentEmergencyStatus;
        currentEmergencyStatus = STATUS_REJECTED;

        runOnUiThread(() -> {
            stopEmergencyAlerts();
            new Handler(Looper.getMainLooper()).postDelayed(() -> stopEmergencyAlerts(), 100);
            new Handler(Looper.getMainLooper()).postDelayed(() -> stopEmergencyAlerts(), 300);

            clearEmergencyNotifications(); // ✅ NEW

            btnConfirm.setVisibility(View.GONE);
            btnReject.setVisibility(View.GONE);
            btnStopSound.setVisibility(View.GONE);

            if (tvEmergencyActionsHeader != null) {
                tvEmergencyActionsHeader.setVisibility(View.GONE);
            }

            setStatisticsCardsEnabled(true);
        });

        if (emergencyId.startsWith("messenger_")) {
            saveCurrentEmergencyToPrefs();
            Toast.makeText(this, "Messenger emergency rejected", Toast.LENGTH_SHORT).show();
            updateEmergencyUI();
            updateEmergencyStatistics();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", STATUS_REJECTED);
        updates.put("rejectedAt", FieldValue.serverTimestamp());

        db.collection("emergency_locations")
                .document(emergencyId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    saveCurrentEmergencyToPrefs();
                    Toast.makeText(this, "Emergency rejected", Toast.LENGTH_SHORT).show();
                    updateEmergencyUI();
                    updateEmergencyStatistics();
                })
                .addOnFailureListener(e -> {
                    currentEmergencyStatus = previousStatus;

                    Toast.makeText(this, "Failed to reject: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    runOnUiThread(() -> {
                        btnConfirm.setVisibility(View.VISIBLE);
                        btnReject.setVisibility(View.VISIBLE);
                        btnStopSound.setVisibility(View.GONE);
                        setStatisticsCardsEnabled(false);

                        if (STATUS_PENDING.equals(currentEmergencyStatus)) {
                            playEmergencyAlerts();
                        }
                    });
                });
    }

    private void forceKillAllSounds() {
        Log.d(TAG, "FORCE KILL ALL SOUNDS - Nuclear option activated");

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {}
        }

        try {
            stopService(new Intent(this, AlertService.class));
        } catch (Exception ignored) {}

        if (audioManager != null) {
            try {
                audioManager.abandonAudioFocus(null);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume > 0 ? previousAlarmVolume : 5, 0);
            } catch (Exception ignored) {}
        }

        try {
            MediaPlayer dummy = new MediaPlayer();
            dummy.reset();
            dummy.release();
        } catch (Exception ignored) {}

        Log.d(TAG, "Force kill completed");
    }

    private void startImageUpdateListener(String emergencyId) {
        if (imageUpdateListener != null) {
            imageUpdateListener.remove();
        }

        if (emergencyId.startsWith("messenger_")) {
            return;
        }

        imageUpdateListener = db.collection("emergency_locations")
                .document(emergencyId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Image update listen failed: " + error.getMessage());
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Boolean hasImage = documentSnapshot.getBoolean("hasImage");
                        String imageBase64 = documentSnapshot.getString("imageBase64");

                        boolean imageChanged = (hasImage != null && hasImage != currentEmergencyHasImage) ||
                                (imageBase64 != null && !imageBase64.equals(currentEmergencyImageBase64));

                        if (imageChanged) {
                            currentEmergencyHasImage = hasImage != null ? hasImage : false;
                            currentEmergencyImageBase64 = imageBase64 != null ? imageBase64 : "";

                            Log.d(TAG, "Image update detected for emergency: " + emergencyId +
                                    ", hasImage: " + currentEmergencyHasImage);

                            runOnUiThread(() -> {
                                btnViewImage.setVisibility(currentEmergencyHasImage ? View.VISIBLE : View.GONE);

                                String statusText = getStatusDisplayText(currentEmergencyStatus);
                                String imageText = currentEmergencyHasImage ? "\n📷 Image Available" : "";
                                tvEmergencyAlert.setText(String.format(Locale.getDefault(),
                                        "%s\nPhone: %s\nReported By: %s%s",
                                        statusText, Phone, Reported, imageText));
                            });

                            saveCurrentEmergencyToPrefs();
                        }
                    }
                });
    }

    private void openGoogleMaps() {
        try {
            String uri = String.format(Locale.ENGLISH, "geo:%f,%f?q=%f,%f(%s)",
                    emergencyLatitude, emergencyLongitude,
                    emergencyLatitude, emergencyLongitude,
                    "Emergency Location");

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                String webUrl = String.format(Locale.ENGLISH,
                        "https://www.google.com/maps/search/?api=1&query=%f,%f",
                        emergencyLatitude, emergencyLongitude);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error opening maps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void playEmergencyAlerts() {
        Log.d(TAG, "playEmergencyAlerts() called");

        // ✅ NEW: Prevent duplicate alert race condition
        if (isPlayingAlert) {
            Log.d(TAG, "Alert already playing, skipping duplicate call");
            return;
        }
        isPlayingAlert = true;

        stopEmergencyAlerts();
        ensureEmergencyVolume();
        requestAudioFocus();

        try {
            mediaPlayer = new MediaPlayer();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.alert);
            if (afd == null) {
                Log.e(TAG, "Could not load alert sound file");
                showFallbackAlert();
                return;
            }

            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(1.0f, 1.0f);

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Log.d(TAG, "MediaPlayer started with ALARM usage");
                startSoundMonitoring();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                showFallbackAlert();
                return false;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaPlayer: " + e.getMessage(), e);
            showFallbackAlert();
        }

        startVibration();
        btnStopSound.setVisibility(View.VISIBLE);
    }

    private void requestAudioFocus() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }

            if (audioManager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(false)
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int focusChange) {
                                switch (focusChange) {
                                    case AudioManager.AUDIOFOCUS_GAIN:
                                        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                                            mediaPlayer.start();
                                        }
                                        break;
                                    case AudioManager.AUDIOFOCUS_LOSS:
                                        stopEmergencyAlerts();
                                        break;
                                }
                            }
                        })
                        .build();

                int result = audioManager.requestAudioFocus(focusRequest);
                Log.d(TAG, "Audio focus request result: " + result);

            } else {
                int result = audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                );
                Log.d(TAG, "Audio focus request result: " + result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error requesting audio focus: " + e.getMessage());
        }
    }

    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                long[] pattern = {0, 500, 250, 500};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting vibration: " + e.getMessage());
            }
        }
    }

    private void showFallbackAlert() {
        runOnUiThread(() -> {
            Toast.makeText(AdminDashboard.this,
                    "Emergency Alert! Audio may be muted. Please check volume.",
                    Toast.LENGTH_LONG).show();
        });

        showEmergencyVolumeNotification();
    }

    private void showEmergencyVolumeNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "EmergencyAlerts")
                .setSmallIcon(R.drawable.baseline_warning_24)
                .setContentTitle("🔊 EMERGENCY ALERT - CHECK VOLUME")
                .setContentText("Ensure device volume is at maximum for emergency alert")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_VOLUME_WARNING, builder.build());
    }

    private void stopEmergencyAlerts() {
        Log.d(TAG, "stopEmergencyAlerts() called - FORCE STOP ALL");

        isPlayingAlert = false; // ✅ NEW: Reset race condition flag
        stopSoundMonitoring();

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "MediaPlayer stopped, reset, and released");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player: " + e.getMessage());
                mediaPlayer = null;
            }
        }

        if (vibrator != null) {
            try {
                vibrator.cancel();
                Log.d(TAG, "Vibration cancelled");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping vibration: " + e.getMessage());
            }
        }

        try {
            Intent stopServiceIntent = new Intent(this, AlertService.class);
            stopService(stopServiceIntent);
            Log.d(TAG, "AlertService stop requested");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping AlertService: " + e.getMessage());
        }

        try {
            if (audioManager != null && previousAlarmVolume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0);
                Log.d(TAG, "Restored alarm volume to: " + previousAlarmVolume);
                previousAlarmVolume = 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring volume: " + e.getMessage());
        }

        if (audioManager != null) {
            try {
                audioManager.abandonAudioFocus(null);
                Log.d(TAG, "Audio focus abandoned");
            } catch (Exception e) {
                Log.e(TAG, "Error abandoning audio focus: " + e.getMessage());
            }
        }

        runOnUiThread(() -> {
            if (btnStopSound != null) {
                btnStopSound.setVisibility(View.GONE);
            }
        });

        Log.d(TAG, "All emergency alerts stopped successfully");
    }

    // ✅ Original single-param overload kept for any internal callers
    private void showNotification(String title, String message) {
        showNotification(title, message, NOTIFICATION_ID_EMERGENCY_ALERT);
    }

    // ✅ NEW: Overloaded method with explicit notification ID
    private void showNotification(String title, String message, int notificationId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "EmergencyAlerts")
                .setSmallIcon(R.drawable.baseline_warning_24)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM);

        notificationManager.notify(notificationId, builder.build());
    }

    // ✅ NEW: Clear emergency notification when confirmed or rejected
    private void clearEmergencyNotifications() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID_EMERGENCY_ALERT);
            Log.d(TAG, "Emergency notification cleared");
        }
    }

    private void requestNotificationListenerPermission() {
        if (!isNotificationListenerEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Enable Notification Access")
                    .setMessage("DQRA needs notification access to automatically detect emergency coordinates from Messenger.\n\nPlease enable 'DQRA' in the next screen.")
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        startActivity(intent);
                    })
                    .setNegativeButton("Later", null)
                    .show();
        }
    }
    private boolean isNotificationListenerEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(packageName);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "EmergencyAlerts",
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH);

            channel.setDescription("Channel for emergency alerts");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500});
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            channel.setSound(null, null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
