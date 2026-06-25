package com.appname.dqra;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.appname.dqra.viewmodel.OperationState;
import com.appname.dqra.viewmodel.RegisterViewModel;

public class RegisterActivity extends AppCompatActivity {

    private EditText fullName, userEmail, userPassword, confirmPassword, userPhone, userAddress;
    private ImageView imgTogglePassword, imgToggleConfirmPassword;
    private Button registerButton;
    private TextView redirectButton;
    private RegisterViewModel viewModel;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        fullName = findViewById(R.id.fullName);
        userEmail = findViewById(R.id.userEmail);
        userPassword = findViewById(R.id.userPassword);
        confirmPassword = findViewById(R.id.confirmPassword);
        imgTogglePassword = findViewById(R.id.imgTogglePassword);
        imgToggleConfirmPassword = findViewById(R.id.imgToggleConfirmPassword);
        userPhone = findViewById(R.id.userPhone);
        userAddress = findViewById(R.id.userAddress);
        registerButton = findViewById(R.id.registerButton);
        redirectButton = findViewById(R.id.RedirectButton);

        setupPasswordToggles();
        observeViewModel();

        registerButton.setOnClickListener(v -> registerUser());

        redirectButton.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void observeViewModel() {
        viewModel.getState().observe(this, state -> {
            if (state == null) {
                return;
            }

            registerButton.setEnabled(state.getStatus() != OperationState.Status.LOADING);

            if (state.getStatus() == OperationState.Status.SUCCESS) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                clearFields();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else if (state.getStatus() == OperationState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerUser() {
        String name = fullName.getText().toString().trim();
        String email = userEmail.getText().toString().trim();
        String password = userPassword.getText().toString().trim();
        String confirmPass = confirmPassword.getText().toString().trim();
        String phone = userPhone.getText().toString().trim();
        String address = userAddress.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password) ||
                TextUtils.isEmpty(confirmPass) || TextUtils.isEmpty(phone) || TextUtils.isEmpty(address)) {
            Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPass)) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.registerUser(name, email, password, phone, address);
    }

    private void clearFields() {
        fullName.setText("");
        userEmail.setText("");
        userPassword.setText("");
        confirmPassword.setText("");
        userPhone.setText("");
        userAddress.setText("");
    }

    private void setupPasswordToggles() {
        if (imgTogglePassword != null) {
            imgTogglePassword.setOnClickListener(v -> {
                if (isPasswordVisible) {
                    userPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    imgTogglePassword.setImageResource(R.drawable.ic_visibility_off);
                    isPasswordVisible = false;
                } else {
                    userPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    imgTogglePassword.setImageResource(R.drawable.ic_visibility);
                    isPasswordVisible = true;
                }
                userPassword.setSelection(userPassword.getText().length());
            });
        }

        if (imgToggleConfirmPassword != null) {
            imgToggleConfirmPassword.setOnClickListener(v -> {
                if (isConfirmPasswordVisible) {
                    confirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    imgToggleConfirmPassword.setImageResource(R.drawable.ic_visibility_off);
                    isConfirmPasswordVisible = false;
                } else {
                    confirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    imgToggleConfirmPassword.setImageResource(R.drawable.ic_visibility);
                    isConfirmPasswordVisible = true;
                }
                confirmPassword.setSelection(confirmPassword.getText().length());
            });
        }
    }
}
