package com.appname.dqra;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.appname.dqra.model.StartDestination;
import com.appname.dqra.repository.AccountRepository;

public class LoginActivity extends AppCompatActivity {

    private static final String LOGIN_RATE_LIMIT_PREFS = "login_rate_limit";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 5 * 60 * 1000L;

    private EditText txtEmail, txtPassword;
    private ImageView imgTogglePassword;
    private Button btnLogin, btnUserRegistration;
    private ProgressBar progressBar;

    private AccountRepository accountRepository;
    private boolean isPasswordVisible = false;
    private SharedPreferences rateLimitPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        accountRepository = new AccountRepository();
        rateLimitPrefs = getSharedPreferences(LOGIN_RATE_LIMIT_PREFS, MODE_PRIVATE);

        txtEmail = findViewById(R.id.txtEmail);
        txtPassword = findViewById(R.id.txtPassword);
        imgTogglePassword = findViewById(R.id.imgTogglePassword);
        btnLogin = findViewById(R.id.btnlogin);
        btnUserRegistration = findViewById(R.id.btnUserRegistration);
        progressBar = findViewById(R.id.ProgressBar);

        setupPasswordToggle();

        btnLogin.setOnClickListener(v -> loginUser());
        btnUserRegistration.setOnClickListener(v ->
                startActivity(new Intent(this, UserRegistrationActivity.class)));
    }

    private void setupPasswordToggle() {
        if (imgTogglePassword == null) {
            return;
        }

        imgTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                txtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                imgTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                txtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                imgTogglePassword.setImageResource(R.drawable.ic_visibility);
            }
            isPasswordVisible = !isPasswordVisible;
            txtPassword.setSelection(txtPassword.getText().length());
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (accountRepository.hasCurrentUser()) {
            startActivity(new Intent(this, ProfileCheckActivity.class));
            finish();
        }
    }

    private void loginUser() {
        if (isLoginLocked()) {
            showLoginLockoutMessage();
            return;
        }

        String email = txtEmail.getText().toString().trim();
        String password = txtPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        accountRepository.loginWithEmail(email, password, new AccountRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                clearLoginRateLimit();
                checkUserRole();
            }

            @Override
            public void onError(Exception exception) {
                progressBar.setVisibility(View.GONE);
                recordFailedLoginAttempt();
                Toast.makeText(LoginActivity.this,
                        buildLoginFailureMessage(exception), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isLoginLocked() {
        long lockoutUntil = rateLimitPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L);
        if (lockoutUntil <= System.currentTimeMillis()) {
            if (lockoutUntil > 0L) {
                clearLoginRateLimit();
            }
            return false;
        }
        return true;
    }

    private void recordFailedLoginAttempt() {
        int attempts = rateLimitPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = rateLimitPrefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
        }
        editor.apply();
    }

    private void clearLoginRateLimit() {
        rateLimitPrefs.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_LOCKOUT_UNTIL)
                .apply();
    }

    private String buildLoginFailureMessage(Exception exception) {
        if (isLoginLocked()) {
            return lockoutMessage();
        }

        int attempts = rateLimitPrefs.getInt(KEY_FAILED_ATTEMPTS, 0);
        int remaining = Math.max(0, MAX_FAILED_ATTEMPTS - attempts);
        String reason = exception == null || exception.getMessage() == null
                ? "Unknown error"
                : exception.getMessage();
        return "Login failed: " + reason + "\nAttempts remaining: " + remaining;
    }

    private void showLoginLockoutMessage() {
        Toast.makeText(this, lockoutMessage(), Toast.LENGTH_LONG).show();
    }

    private String lockoutMessage() {
        long remainingMs = Math.max(0L,
                rateLimitPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis());
        long minutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        return "Too many failed login attempts. Please wait " + minutes + " minute(s).";
    }

    private void checkUserRole() {
        progressBar.setVisibility(View.VISIBLE);
        accountRepository.resolveLoginRole(new AccountRepository.Callback<StartDestination>() {
            @Override
            public void onSuccess(StartDestination value) {
                progressBar.setVisibility(View.GONE);
                if (value == StartDestination.ADMIN_DASHBOARD) {
                    startActivity(new Intent(LoginActivity.this, AdminDashboard.class));
                    finish();
                } else {
                    navigateThroughProfileCheck();
                }
            }

            @Override
            public void onError(Exception exception) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateThroughProfileCheck() {
        startActivity(new Intent(this, ProfileCheckActivity.class));
        finish();
    }
}
