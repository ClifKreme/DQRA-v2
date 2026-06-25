package com.appname.dqra.listeners;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.appname.dqra.R;
import com.appname.dqra.maindashboards.AdminDashboard;

public class AlertService extends NotificationListenerService {

    public static final String TAG = "DQRA";
    public static final String ACTION_STOP = "com.appname.dqra.messengeralert.ACTION_STOP";

    // Track active instance to prevent duplicate alerts
    private static AlertService instance;

    private Handler mapHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int previousVolume;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    private Vibrator vibrator;

    // Receiver to stop the alert when signaled
    private BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "stopReceiver received action=" + (intent != null ? intent.getAction() : "null"));
            if (intent != null && ACTION_STOP.equals(intent.getAction())) {
                stopAlert();
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "AlertService onCreate");

        IntentFilter filter = new IntentFilter(ACTION_STOP);
        try {
            // Register as NOT exported so other apps cannot send this broadcast
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(stopReceiver, filter);
            }
            Log.i(TAG, "Stop receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register stop receiver: " + e.getMessage(), e);
        }

        instance = this;

        // Initialize vibrator safely across Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = getSystemService(VibratorManager.class);
            if (vibratorManager != null) {
                vibrator = vibratorManager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator != null) {
            Log.i(TAG, "Vibrator initialized");
        } else {
            Log.e(TAG, "Failed to initialize vibrator");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        String packageName = sbn.getPackageName();

        // Only process Messenger notifications
        if (!"com.facebook.orca".equals(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        if (extras == null) {
            Log.w(TAG, "Notification extras are null");
            return;
        }

        String senderName = "Unknown User";
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title != null) {
            senderName = title.toString();
        }

        String messageText = "";
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text != null) {
            messageText = text.toString();
        }

        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bigText != null && bigText.length() > messageText.length()) {
            messageText = bigText.toString();
        }

        Log.i(TAG, "Messenger notification from: " + senderName);
        Log.i(TAG, "Message: " + messageText);

        String latLonPattern = "(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(latLonPattern);
        java.util.regex.Matcher matcher = pattern.matcher(messageText);

        if (matcher.find()) {
            String lat = matcher.group(1);
            String lon = matcher.group(2);

            Log.i(TAG, "Coordinates found: " + lat + ", " + lon + " from sender: " + senderName);

            Intent serviceIntent = new Intent(this, AlertService.class);
            serviceIntent.putExtra("lat", lat);
            serviceIntent.putExtra("lon", lon);
            serviceIntent.putExtra("senderName", senderName);
            serviceIntent.putExtra("messageText", messageText);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Log.i(TAG, "AlertService started with coordinates and sender info");
        } else {
            Log.d(TAG, "No coordinates found in Messenger notification");
        }
    }

    // Helper to build PendingIntent flags correctly
    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand action=" + (intent != null ? intent.getAction() : "null"));

        // If started with ACTION_STOP, stop immediately
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.i(TAG, "Received ACTION_STOP via startService - stopping");
            stopAlert();
            return START_NOT_STICKY;
        }

        String lat = intent != null ? intent.getStringExtra("lat") : null;
        String lon = intent != null ? intent.getStringExtra("lon") : null;
        String senderName = intent != null ? intent.getStringExtra("senderName") : "Unknown User";
        String reportedBy = intent != null ? intent.getStringExtra("reportedBy") : null;
        String phoneNumber = intent != null ? intent.getStringExtra("phoneNumber") : null;
        String stationName = intent != null ? intent.getStringExtra("stationName") : null;
        String messageText = intent != null ? intent.getStringExtra("messageText") : "";
        reportedBy = firstNonEmpty(reportedBy, extractField(messageText, "Name"), senderName, "Messenger User");
        phoneNumber = firstNonEmpty(phoneNumber, extractField(messageText, "Phone"), "Via Messenger");

        Log.i(TAG, "Processing coordinates: " + lat + ", " + lon + " from: " + reportedBy);

        // Stop any currently running alert (for consecutive messages)
        if (instance != null && instance != this) {
            Log.i(TAG, "Another alert instance found - stopping previous alert before new one");
            instance.stopAlert();
        }

        instance = this;

        String channelId = "draq_alert_channel";
        createNotificationChannel(channelId);

        Intent openAppIntent = buildAdminDashboardIntent(lat, lon, reportedBy, phoneNumber, stationName, messageText);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                this,
                1001,
                openAppIntent,
                pendingFlags()
        );

        Notification serviceNotification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("DQRA Emergency Alert")
                .setContentText("From: " + reportedBy + " | Coords: " + lat + ", " + lon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openAppPendingIntent)
                .setFullScreenIntent(openAppPendingIntent, true)
                .build();

        startForeground(1, serviceNotification);

        // Trigger the AdminDashboard alert system with sender info
        triggerAdminDashboardAlert(lat, lon, reportedBy, phoneNumber, stationName, messageText);

        if (lat != null && lon != null) {
            launchAdminDashboard(lat, lon, reportedBy, phoneNumber, stationName, messageText);
        }

        return START_NOT_STICKY;
    }

    private void triggerAdminDashboardAlert(String lat, String lon, String reportedBy,
                                            String phoneNumber, String stationName, String messageText) {
        try {
            // Send broadcast to trigger your existing AdminDashboard alerts
            Intent adminAlertIntent = new Intent("EMERGENCY_ALERT_RECEIVED");
            adminAlertIntent.putExtra("latitude", lat);
            adminAlertIntent.putExtra("longitude", lon);
            adminAlertIntent.putExtra("source", "messenger");
            adminAlertIntent.putExtra("reportedBy", reportedBy);
            adminAlertIntent.putExtra("phoneNumber", phoneNumber);
            adminAlertIntent.putExtra("stationName", stationName);
            adminAlertIntent.putExtra("messageText", messageText);
            sendBroadcast(adminAlertIntent);

            Log.i(TAG, "Admin dashboard alert triggered for coordinates: " + lat + ", " + lon +
                    " from: " + reportedBy);
        } catch (Exception e) {
            Log.e(TAG, "Error triggering admin dashboard alert: " + e.getMessage());
        }
    }

    private Intent buildAdminDashboardIntent(String lat, String lon, String reportedBy,
                                             String phoneNumber, String stationName, String messageText) {
        Intent intent = new Intent(this, AdminDashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (lat != null) intent.putExtra("lat", lat);
        if (lon != null) intent.putExtra("lon", lon);
        intent.putExtra("senderName", reportedBy);
        intent.putExtra("reportedBy", reportedBy);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("stationName", stationName);
        intent.putExtra("messageText", messageText);
        intent.putExtra("EMERGENCY_NOTIFICATION", true);
        return intent;
    }

    private void launchAdminDashboard(String lat, String lon, String reportedBy,
                                      String phoneNumber, String stationName, String messageText) {
        Intent adminIntent = buildAdminDashboardIntent(lat, lon, reportedBy, phoneNumber, stationName, messageText);
        try {
            startActivity(adminIntent);
            Log.i(TAG, "AdminDashboard launch requested for Messenger emergency");
        } catch (Exception e) {
            Log.w(TAG, "Direct dashboard launch blocked; full-screen notification remains available", e);
        }
    }

    private String extractField(String message, String label) {
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

    private void stopAlert() {
        Log.i(TAG, "stopAlert() called");
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.i(TAG, "MediaPlayer stopped and released");
            }
        } catch (Exception e) {
            Log.e(TAG, "stopAlert media error: " + e.getMessage());
        }

        try {
            if (vibrator != null) {
                vibrator.cancel();
                Log.i(TAG, "Vibrator cancelled");
            }
        } catch (Exception e) {
            Log.e(TAG, "vibrator cancel error: " + e.getMessage());
        }

        // restore volume
        try {
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0);
                Log.i(TAG, "Volume restored to: " + previousVolume);
            }
        } catch (Exception e) {
            Log.e(TAG, "restore volume error: " + e.getMessage());
        }

        // release audio focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null && audioManager != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                }
            } else {
                if (audioManager != null) {
                    audioManager.abandonAudioFocus(null);
                }
            }
            Log.i(TAG, "Audio focus abandoned");
        } catch (Exception e) {
            Log.e(TAG, "abandon focus error: " + e.getMessage());
        }

        // cancel notifications
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancelAll();
                Log.i(TAG, "Notifications cancelled");
            }
        } catch (Exception e) {
            Log.e(TAG, "cancel notifications error: " + e.getMessage());
        }

        mapHandler.removeCallbacksAndMessages(null);
        handler.removeCallbacksAndMessages(null);
        instance = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        Log.i(TAG, "AlertService stopped completely");
    }

    private void createNotificationChannel(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "DQRA Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Channel for DQRA foreground alerts");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setBypassDnd(true);

            // Set sound to null to avoid conflict with our alarm sound
            channel.setSound(null, null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.i(TAG, "Notification channel created: " + channelId);
            } else {
                Log.e(TAG, "NotificationManager is null");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "AlertService onDestroy");
        try {
            unregisterReceiver(stopReceiver);
            Log.i(TAG, "Stop receiver unregistered");
        } catch (Exception e) {
            Log.w(TAG, "unregisterReceiver failed: " + e.getMessage());
        }
        stopAlert();
    }
}
