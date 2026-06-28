package com.appname.dqra;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.appname.dqra.repository.FeedbackRepository;

public class HelpSupportActivity extends AppCompatActivity {
    private FeedbackRepository feedbackRepository;
    private EditText etFeedbackMessage;
    private RatingBar ratingFeedback;
    private Button btnSubmitFeedback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        feedbackRepository = new FeedbackRepository();

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        etFeedbackMessage = findViewById(R.id.etFeedbackMessage);
        ratingFeedback = findViewById(R.id.ratingFeedback);
        btnSubmitFeedback = findViewById(R.id.btnSubmitFeedback);
        btnSubmitFeedback.setOnClickListener(v -> submitFeedback());
    }

    private void submitFeedback() {
        String message = etFeedbackMessage.getText().toString().trim();
        int rating = Math.round(ratingFeedback.getRating());

        if (rating <= 0) {
            Toast.makeText(this, "Please choose a rating.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() < 10) {
            Toast.makeText(this, "Please write at least 10 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitFeedback.setEnabled(false);
        btnSubmitFeedback.setText("Submitting...");

        feedbackRepository.submitFeedback(message, rating, new FeedbackRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                Toast.makeText(HelpSupportActivity.this,
                        "Feedback submitted. Thank you!", Toast.LENGTH_LONG).show();
                etFeedbackMessage.setText("");
                ratingFeedback.setRating(0);
                btnSubmitFeedback.setEnabled(true);
                btnSubmitFeedback.setText("Submit Feedback");
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(HelpSupportActivity.this, exception.getMessage(),
                        Toast.LENGTH_LONG).show();
                btnSubmitFeedback.setEnabled(true);
                btnSubmitFeedback.setText("Submit Feedback");
            }
        });
    }
}
