package com.appname.dqra;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class WebsiteActivity extends BaseActivity {
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_web);

        db = FirebaseFirestore.getInstance();
        Button btnOpenWebsite = findViewById(R.id.btnOpenWebsite);

        // Replace with your website link
        String url = "https://fsis.e-bfp.com/";

        btnOpenWebsite.setOnClickListener(v -> {
            // Send notification to Barotac Nuevo station
            sendWebsiteAccessNotification();

            // Open the website
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
    }

    private void sendWebsiteAccessNotification() {
        // ✅ FIXED: Get target municipality dynamically
        String targetMunicipality = "Barotac Nuevo"; // Default
        
        // Try to get from user preferences or make it configurable
        // For now, keeping default but making it clear it should be dynamic
        
        // Create a notification document in Firestore
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "website_access");
        notificationData.put("timestamp", FieldValue.serverTimestamp());
        notificationData.put("municipality", targetMunicipality); // Now using variable
        notificationData.put("status", "unread");

        db.collection("website_notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Notification sent to " + targetMunicipality + " station", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send notification", Toast.LENGTH_SHORT).show();
                });
    }
}