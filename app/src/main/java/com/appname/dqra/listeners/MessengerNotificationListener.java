package com.appname.dqra.listeners;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessengerNotificationListener extends NotificationListenerService {
    private static final String TAG = "DQRA";
    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("LOC:\\s*(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }

        String pkg = sbn.getPackageName().toLowerCase();
        if (!pkg.contains("messenger") && !pkg.contains("facebook.orca")) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification != null ? notification.extras : null;
        String message = buildNotificationMessage(extras);

        Log.d(TAG, "Messenger Notification detected: " + message);

        Matcher matcher = COORDINATE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return;
        }

        String lat = matcher.group(1);
        String lon = matcher.group(2);
        String notificationTitle = getNotificationText(extras, Notification.EXTRA_TITLE, "");
        String reportedBy = firstNonEmpty(extractField(message, "Name"), notificationTitle, "Messenger User");
        String phoneNumber = firstNonEmpty(extractField(message, "Phone"), "Via Messenger");
        String stationName = extractStationName(message);

        Log.i(TAG, "Coordinates detected: " + lat + ", " + lon + " reported by " + reportedBy);

        try {
            Intent alertIntent = new Intent(this, AlertService.class);
            alertIntent.putExtra("lat", lat);
            alertIntent.putExtra("lon", lon);
            alertIntent.putExtra("senderName", reportedBy);
            alertIntent.putExtra("reportedBy", reportedBy);
            alertIntent.putExtra("phoneNumber", phoneNumber);
            alertIntent.putExtra("stationName", stationName);
            alertIntent.putExtra("messageText", message);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(alertIntent);
            } else {
                startService(alertIntent);
            }

            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(
                            getApplicationContext(),
                            "Coordinates received: " + lat + ", " + lon,
                            Toast.LENGTH_SHORT
                    ).show());

        } catch (Exception e) {
            Log.e(TAG, "Error starting AlertService: " + e.getMessage(), e);
        }
    }

    private String buildNotificationMessage(Bundle extras) {
        if (extras == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, getNotificationText(extras, Notification.EXTRA_TEXT, ""));
        appendIfPresent(builder, getNotificationText(extras, Notification.EXTRA_BIG_TEXT, ""));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                appendIfPresent(builder, line != null ? line.toString() : "");
            }
        }

        return builder.toString();
    }

    private String getNotificationText(Bundle extras, String key, String fallback) {
        if (extras == null) {
            return fallback;
        }
        CharSequence value = extras.getCharSequence(key);
        return value != null ? value.toString() : fallback;
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        String trimmed = value.trim();
        if (builder.indexOf(trimmed) >= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(trimmed);
    }

    private String extractField(String message, String label) {
        Pattern pattern = Pattern.compile("(?im)^\\s*" + Pattern.quote(label) + "\\s*:\\s*(.+?)\\s*$");
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractStationName(String message) {
        Pattern pattern = Pattern.compile("(?i)Need assistance from\\s+(.+?)!");
        Matcher matcher = pattern.matcher(message);
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

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            Log.d(TAG, "Notification removed from " + sbn.getPackageName());
        }
    }

    @Override
    public void onListenerConnected() {
        Log.i(TAG, "Notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        Log.w(TAG, "Notification listener disconnected; requesting rebind.");
        try {
            requestRebind(new ComponentName(this, MessengerNotificationListener.class));
        } catch (Exception e) {
            Log.e(TAG, "Listener rebind failed: " + e.getMessage());
        }
    }
}
