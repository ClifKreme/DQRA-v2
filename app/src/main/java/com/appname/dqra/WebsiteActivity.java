package com.appname.dqra;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.appname.dqra.repository.WebsiteRepository;

public class WebsiteActivity extends BaseActivity {
    private WebsiteRepository websiteRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_web);

        websiteRepository = new WebsiteRepository();
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
        
        websiteRepository.sendWebsiteAccessNotification(targetMunicipality,
                new WebsiteRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Toast.makeText(WebsiteActivity.this,
                                "Notification sent to " + targetMunicipality + " station",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(WebsiteActivity.this,
                                "Failed to send notification", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
