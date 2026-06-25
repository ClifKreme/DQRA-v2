package com.appname.dqra;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.appname.dqra.viewmodel.CompleteProfileViewModel;
import com.appname.dqra.viewmodel.OperationState;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class CompleteProfileActivity extends AppCompatActivity {

    private ScrollView scrollView;
    private TextInputEditText etFullName, etPhone, etAddress;
    private TextInputLayout tilFullName, tilPhone, tilAddress;
    private Button btnComplete;
    private ProgressBar progressBar;
    private TextView tvTitle, tvSubtitle;
    private CompleteProfileViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_profile);

        viewModel = new ViewModelProvider(this).get(CompleteProfileViewModel.class);

        initializeViews();
        observeViewModel();
        setupAddressAutoScroll();

        btnComplete.setOnClickListener(v -> completeProfile());
    }

    private void initializeViews() {
        scrollView = findViewById(R.id.scrollView);
        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        tilAddress = findViewById(R.id.tilAddress);
        btnComplete = findViewById(R.id.btnComplete);
        progressBar = findViewById(R.id.progressBar);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
    }

    private void observeViewModel() {
        viewModel.getState().observe(this, state -> {
            if (state == null) {
                return;
            }

            boolean loading = state.getStatus() == OperationState.Status.LOADING;
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnComplete.setEnabled(!loading);

            if (state.getStatus() == OperationState.Status.SUCCESS) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, ProfileCheckActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else if (state.getStatus() == OperationState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAddressAutoScroll() {
        etAddress.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                view.postDelayed(() -> scrollAddressFieldIntoView(), 300);
            }
        });

        etAddress.setOnClickListener(view ->
                view.postDelayed(this::scrollAddressFieldIntoView, 200));
    }

    private void scrollAddressFieldIntoView() {
        int[] location = new int[2];
        etAddress.getLocationOnScreen(location);
        int addressFieldY = location[1];
        int scrollTo = addressFieldY - 200;
        scrollView.smoothScrollTo(0, scrollTo);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Toast.makeText(this, "Please complete your profile to continue", Toast.LENGTH_SHORT).show();
    }

    private void completeProfile() {
        String fullName = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (!validateInputs(fullName, phone, address)) {
            return;
        }

        viewModel.completeProfile(fullName, phone, address);
    }

    private boolean validateInputs(String fullName, String phone, String address) {
        boolean isValid = true;

        etFullName.setError(null);
        etPhone.setError(null);
        etAddress.setError(null);

        if (fullName.isEmpty()) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            isValid = false;
        }

        if (phone.isEmpty()) {
            etPhone.setError("Phone number is required");
            if (isValid) etPhone.requestFocus();
            isValid = false;
        } else if (phone.length() < 10) {
            etPhone.setError("Please enter a valid phone number");
            if (isValid) etPhone.requestFocus();
            isValid = false;
        }

        if (address.isEmpty()) {
            etAddress.setError("Address is required");
            if (isValid) etAddress.requestFocus();
            isValid = false;
        }

        return isValid;
    }
}
