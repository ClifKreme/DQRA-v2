package com.appname.dqra;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;

import com.appname.dqra.mainregister.MainAdminRegister;
import com.appname.dqra.repository.AccountRepository;
import com.appname.dqra.repository.UserRegistrationRepository;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * ISUFSTMAIN - Main landing screen.
 * Provides navigation to Register Admin or Logout.
 */
public class ISUFSTMAIN extends AppCompatActivity {
    private static final String NOTIF_CHANNEL_ID = "RegistrationRequests";
    private static final String NOTIF_CHANNEL_NAME = "Registration Requests";
    private static final int NOTIF_ID_NEW_REQ = 2001;

    private AccountRepository accountRepository;
    private UserRegistrationRepository registrationRepository;
    private ListenerRegistration pendingCountListener;
    private TextView tvRegistrationRequestsBadge;
    private int lastKnownPendingCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        accountRepository = new AccountRepository();
        registrationRepository = new UserRegistrationRepository(this);
        createNotificationChannel();

        // CardView acting as the Register Admin button
        CardView btnRegister = findViewById(R.id.btn1);
        CardView btnRegistrationRequests = findViewById(R.id.btnRegistrationRequests);
        CardView btnEmergencyContacts = findViewById(R.id.btnManageEmergencyContacts);
        CardView btnViewAppFeedback = findViewById(R.id.btnViewAppFeedback);
        Button btnLogout = findViewById(R.id.btnLogout);
        tvRegistrationRequestsBadge = findViewById(R.id.tvRegistrationRequestsBadge);

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(ISUFSTMAIN.this, MainAdminRegister.class);
            startActivity(intent);
        });

        btnRegistrationRequests.setOnClickListener(v -> {
            Intent intent = new Intent(ISUFSTMAIN.this, AdminRegistrationRequestsActivity.class);
            startActivity(intent);
        });

        btnEmergencyContacts.setOnClickListener(v -> {
            Intent intent = new Intent(ISUFSTMAIN.this, AddEmergencyContactActivity.class);
            startActivity(intent);
        });

        btnViewAppFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(ISUFSTMAIN.this, AppFeedbackActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> logoutUser());
        startPendingRequestsListener();
    }

    @Override
    protected void onDestroy() {
        if (pendingCountListener != null) {
            pendingCountListener.remove();
            pendingCountListener = null;
        }
        super.onDestroy();
    }

    private void startPendingRequestsListener() {
        pendingCountListener = registrationRepository.listenForPendingRequests(
                new UserRegistrationRepository.RequestListener() {
                    @Override
                    public void onRequestsChanged(java.util.List<com.appname.dqra.model.UserRegistrationRequest> requests) {
                        int count = requests.size();
                        updateRegistrationRequestsBadge(count);
                        if (lastKnownPendingCount >= 0 && count > lastKnownPendingCount) {
                            fireNewRequestNotification(count - lastKnownPendingCount);
                        }
                        lastKnownPendingCount = count;
                    }

                    @Override
                    public void onError(Exception exception) {
                        // Keep the main dashboard usable if the badge listener fails.
                    }
                });
    }

    private void updateRegistrationRequestsBadge(int count) {
        if (tvRegistrationRequestsBadge == null) {
            return;
        }

        if (count <= 0) {
            tvRegistrationRequestsBadge.setVisibility(View.GONE);
            return;
        }

        tvRegistrationRequestsBadge.setVisibility(View.VISIBLE);
        tvRegistrationRequestsBadge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private void fireNewRequestNotification(int newCount) {
        String title = newCount == 1
                ? "New Registration Request"
                : newCount + " New Registration Requests";
        String body = newCount == 1
                ? "A user has submitted a registration request. Tap to review."
                : newCount + " users have submitted registration requests. Tap to review.";

        Intent intent = new Intent(this, AdminRegistrationRequestsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_person)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIF_ID_NEW_REQ, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    NOTIF_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts when users submit registration requests");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Signs out the current user and redirects to Login screen.
     */
    private void logoutUser() {
        accountRepository.signOut();
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
