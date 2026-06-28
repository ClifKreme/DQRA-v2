package com.appname.dqra;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.AppFeedback;
import com.appname.dqra.repository.FeedbackRepository;

import java.util.List;

public class AppFeedbackActivity extends AppCompatActivity {
    private FeedbackRepository feedbackRepository;
    private AppFeedbackAdapter adapter;
    private TextView tvEmptyFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_feedback);

        feedbackRepository = new FeedbackRepository();
        adapter = new AppFeedbackAdapter();

        ImageButton btnBack = findViewById(R.id.btnFeedbackBack);
        btnBack.setOnClickListener(v -> finish());

        tvEmptyFeedback = findViewById(R.id.tvEmptyFeedback);
        RecyclerView recyclerView = findViewById(R.id.rvAppFeedback);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadFeedback();
    }

    private void loadFeedback() {
        feedbackRepository.fetchFeedback(new FeedbackRepository.Callback<List<AppFeedback>>() {
            @Override
            public void onSuccess(List<AppFeedback> feedback) {
                adapter.setData(feedback);
                tvEmptyFeedback.setVisibility(feedback.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(AppFeedbackActivity.this,
                        "Failed to load feedback: " + exception.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
