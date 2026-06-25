package com.appname.dqra;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class HelpSupportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Hotline — tap to call
        findViewById(R.id.tvHotline).setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:0631234567"));
            startActivity(callIntent);
        });

        // Email — tap to compose
        findViewById(R.id.tvEmail).setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:support@dqra.app"));
            startActivity(emailIntent);
        });
    }
}
