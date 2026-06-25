package com.appname.dqra.image;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.appname.dqra.R;

public class FullScreenImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make activity full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_fullscreen_image);

        ImageView fullScreenImageView = findViewById(R.id.fullScreenImageView);
        View closeButton = findViewById(R.id.btnClose);

        String imageBase64 = getIntent().getStringExtra("image_base64");

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            Base64ImageHelper.loadBase64IntoImageView(fullScreenImageView, imageBase64);

            // Enable pinch-to-zoom functionality
            fullScreenImageView.setOnTouchListener(new View.OnTouchListener() {
                private float scaleFactor = 1.0f;
                private ScaleGestureDetector scaleGestureDetector;

                {
                    scaleGestureDetector = new ScaleGestureDetector(FullScreenImageActivity.this,
                            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                @Override
                                public boolean onScale(ScaleGestureDetector detector) {
                                    scaleFactor *= detector.getScaleFactor();
                                    scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f));
                                    fullScreenImageView.setScaleX(scaleFactor);
                                    fullScreenImageView.setScaleY(scaleFactor);
                                    return true;
                                }
                            });
                }

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    scaleGestureDetector.onTouchEvent(event);
                    return true;
                }
            });
        }

        closeButton.setOnClickListener(v -> finish());
    }
}