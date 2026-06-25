package com.appname.dqra.listeners;

// Import necessary Android and Firebase libraries
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.appname.dqra.R;
import com.appname.dqra.maindashboards.AdminDashboard;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Service that monitors Firestore for emergency updates and sends notifications
 * Runs in the background as a foreground service with a persistent notification
 */
public class FirestoreService extends Service {
    // Constants for logging, notification channel, and preferences
    private static final String TAG = "FirestoreService";
    private static final String CHANNEL_ID = "FirestoreServiceChannel";
    private static final String ALERT_CHANNEL_ID = "EmergencyLaunchAlerts";
    private static final String PREFS_NAME = "EmergencyPrefs";
    private static final String KEY_LAST_TIMESTAMP = "lastEmergencyTimestamp";
    private static final long NOTIFICATION_DEBOUNCE_TIME = 5000; // 5 seconds debounce time to prevent duplicate notifications

    // Member variables for service operation
    private PowerManager.WakeLock wakeLock; // Keeps CPU running to handle background processing
    private ListenerRegistration emergencyListener; // Registration object for Firestore listener
    private SharedPreferences sharedPreferences; // For persisting the last emergency timestamp
    private long lastProcessedTimestamp = 0; // Tracks the most recent emergency timestamp processed
    private long lastAppLaunchTime = 0; // Tracks when the app was last launched to prevent spam
    private boolean isAppInForeground = false; // Tracks if the app is currently visible to the user
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private View emergencyOverlayView;

    /**
     * Initialize the service and load saved preferences
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // Get stored preferences and retrieve the last processed emergency timestamp
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        lastProcessedTimestamp = sharedPreferences.getLong(KEY_LAST_TIMESTAMP, 0);
    }

    /**
     * Called when service is started. Sets up notification channel and begins listening for updates.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create notification channel for Android O and above
        createNotificationChannel();
        createEmergencyAlertChannel();

        // Start as foreground service with proper service type for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createPersistentNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, createPersistentNotification());
        }
        // Keep device awake while service runs
        acquireWakeLock();
        // Begin monitoring Firestore for emergency updates
        listenForFirestoreUpdates();
        // Update app foreground status based on intent extra
        isAppInForeground = intent != null && intent.getBooleanExtra("isForeground", false);
        // Restart if killed by system
        return START_STICKY;
    }

    /**
     * Clean up resources when service is destroyed
     */
    @Override
    public void onDestroy() {
        // Release wake lock to save battery
        releaseWakeLock();
        removeEmergencyOverlay();
        // Remove Firestore listeners to prevent memory leaks
        removeFirestoreListener();
        super.onDestroy();
    }

    /**
     * Service does not support binding
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Acquire a partial wake lock to keep CPU running for background processing
     */
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":WakeLock");
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/); // Set timeout to prevent battery drain
    }

    /**
     * Release wake lock when no longer needed
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    /**
     * Clean up Firestore listener when not needed
     */
    private void removeFirestoreListener() {
        if (emergencyListener != null) {
            emergencyListener.remove();
        }
    }

    /**
     * Create the persistent notification required for foreground service
     */
    private Notification createPersistentNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Emergency Monitoring")
                .setContentText("Listening for emergencies in your area")
                .setSmallIcon(R.drawable.baseline_warning_24)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to minimize user disruption
                .setOngoing(true) // Cannot be dismissed by user
                .build();
    }

    /**
     * Create the notification channel required for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW // Low importance for persistent notification
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Set up listeners for emergency updates in Firestore based on user role
     */
    private void listenForFirestoreUpdates() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentUserId = getCurrentUserId();

        if (currentUserId == null) {
            stopSelf();
            return;
        }

        // Check if user is an admin
        checkAdminStatus(db, currentUserId);
    }

    private void checkAdminStatus(FirebaseFirestore db, String userId) {
        db.collection("admins_with_stations").document(userId).get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        // If user is an admin, get their municipality and set up listener
                        String municipality = adminDoc.getString("municipality");
                        if (municipality != null) {
                            setupEmergencyListener(db, municipality, AdminDashboard.class);
                        } else {
                            // Stop service if admin has no municipality assigned
                            stopSelf();
                        }
                    } else {
                        // Stop service if user is not an admin
                        stopSelf();
                    }
                })
                .addOnFailureListener(e -> stopSelf());
    }

    /**
     * Configure the emergency listener for the user's municipality
     */
    private void setupEmergencyListener(FirebaseFirestore db, String municipality, Class<?> targetActivity) {
        // Clean up any existing listener first
        removeFirestoreListener();

        // Set up real-time listener for the most recent emergency
        emergencyListener = db.collection("emergency_locations")
                .orderBy("timestamp", Query.Direction.DESCENDING) // Sort by newest first
                .limit(1) // Get only the most recent emergency
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || snapshots.isEmpty()) {
                        return;
                    }

                    // Process each emergency document (should only be one due to limit(1))
                    for (QueryDocumentSnapshot document : snapshots) {
                        processEmergency(document, municipality, targetActivity);
                    }
                });
    }

    /**
     * Process a new emergency document and send notification if relevant
     */
    private void processEmergency(QueryDocumentSnapshot document, String userMunicipality, Class<?> targetActivity) {
        // Extract emergency details
        String emergencyMunicipality = document.getString("municipality");
        Long timestamp = document.getLong("timestamp");
        String emergencyId = document.getId();

        // Ignore emergencies that don't match user's municipality or have missing data
        if (emergencyMunicipality == null || timestamp == null || !emergencyMunicipality.equals(userMunicipality)) {
            return;
        }

        // Only process newer emergencies than we've seen before
        if (timestamp > lastProcessedTimestamp) {
            // Update the last processed timestamp
            lastProcessedTimestamp = timestamp;
            sharedPreferences.edit().putLong(KEY_LAST_TIMESTAMP, timestamp).apply();

            // Apply debounce logic to prevent rapid successive launches
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastAppLaunchTime < NOTIFICATION_DEBOUNCE_TIME) {
                return;
            }
            lastAppLaunchTime = currentTime;

            // Only launch activity if app is not already visible to user
            if (!isAppInForeground) {
                launchEmergencyActivity(emergencyId, targetActivity);
            }
            // ✅ FIXED: Removed duplicate notification - AdminDashboard handles notifications
            // showEmergencyNotification(emergencyId, targetActivity);
        }
    }

    /**
     * Launch the appropriate activity when a new emergency is detected
     */
    private void launchEmergencyActivity(String emergencyId, Class<?> targetActivity) {
        Intent intent = new Intent(this, targetActivity);
        intent.putExtra("EMERGENCY_NOTIFICATION", true);
        intent.putExtra("emergencyId", emergencyId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        showEmergencyNotification(emergencyId, targetActivity);

        if (canUseOverlayLaunch()) {
            showEmergencyOverlayThenLaunch(intent);
            return;
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Background activity launch blocked; notification fallback is active", e);
        }
    }

    /**
     * Display a high-priority notification for the new emergency
     */
    private void showEmergencyNotification(String emergencyId, Class<?> targetActivity) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Create intent for when notification is tapped
        Intent intent = new Intent(this, targetActivity);
        intent.putExtra("EMERGENCY_NOTIFICATION", true);
        intent.putExtra("emergencyId", emergencyId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // ✅ FIXED: Prevent integer overflow
        int notificationId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        // Create pending intent that will launch activity when notification is tapped
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId, // Use same ID for uniqueness
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE // Update existing intent if any
        );

        // Build and show the emergency notification
        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_warning_24)
                .setContentTitle("Emergency Alert")
                .setContentText("New emergency in your area")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true) // Dismiss when tapped
                .build();

        // Post notification with unique ID based on timestamp
        manager.notify(notificationId, notification);
    }

    private boolean canUseOverlayLaunch() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void showEmergencyOverlayThenLaunch(Intent intent) {
        mainHandler.post(() -> {
            try {
                removeEmergencyOverlay();

                WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (windowManager == null) {
                    startActivity(intent);
                    return;
                }

                LinearLayout overlay = new LinearLayout(this);
                overlay.setOrientation(LinearLayout.VERTICAL);
                overlay.setGravity(Gravity.CENTER);
                overlay.setPadding(48, 48, 48, 48);
                overlay.setBackgroundColor(Color.argb(235, 183, 28, 28));

                TextView title = new TextView(this);
                title.setText("DQRA EMERGENCY ALERT");
                title.setTextColor(Color.WHITE);
                title.setTextSize(24);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

                TextView subtitle = new TextView(this);
                subtitle.setText("Opening emergency dashboard...");
                subtitle.setTextColor(Color.WHITE);
                subtitle.setTextSize(18);
                subtitle.setGravity(Gravity.CENTER);
                subtitle.setPadding(0, 18, 0, 0);

                overlay.addView(title, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                overlay.addView(subtitle, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        overlayType,
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.CENTER;

                emergencyOverlayView = overlay;
                windowManager.addView(emergencyOverlayView, params);

                mainHandler.postDelayed(() -> {
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.w(TAG, "Overlay-assisted launch was blocked", e);
                    }
                }, 250);

                mainHandler.postDelayed(this::removeEmergencyOverlay, 5000);
            } catch (Exception e) {
                Log.e(TAG, "Error showing emergency overlay", e);
                try {
                    startActivity(intent);
                } catch (Exception launchError) {
                    Log.w(TAG, "Fallback activity launch blocked", launchError);
                }
            }
        });
    }

    private void removeEmergencyOverlay() {
        mainHandler.post(() -> {
            if (emergencyOverlayView == null) {
                return;
            }
            try {
                WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (windowManager != null) {
                    windowManager.removeView(emergencyOverlayView);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error removing emergency overlay", e);
            } finally {
                emergencyOverlayView = null;
            }
        });
    }

    private void createEmergencyAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Emergency Launch Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Urgent emergency alerts that open the admin dashboard");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            channel.setBypassDnd(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Helper method to get the current user's ID from Firebase Authentication
     */
    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}
