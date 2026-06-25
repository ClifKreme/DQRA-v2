package com.appname.dqra;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.appname.dqra.model.StartDestination;
import com.appname.dqra.repository.AccountRepository;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;

    private EditText txtEmail, txtPassword;
    private ImageView imgTogglePassword;
    private Button btnLogin;
    private TextView tRegister;
    private ProgressBar progressBar;
    private androidx.cardview.widget.CardView btnGoogle;

    private AccountRepository accountRepository;
    private GoogleSignInClient googleSignInClient;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        accountRepository = new AccountRepository();

        txtEmail = findViewById(R.id.txtEmail);
        txtPassword = findViewById(R.id.txtPassword);
        imgTogglePassword = findViewById(R.id.imgTogglePassword);
        btnLogin = findViewById(R.id.btnlogin);
        progressBar = findViewById(R.id.ProgressBar);
        tRegister = findViewById(R.id.txtregister);
        btnGoogle = findViewById(R.id.btnGoogle);

        setupPasswordToggle();

        btnLogin.setOnClickListener(v -> loginUser());
        tRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        btnGoogle.setOnClickListener(v -> signInWithGoogle());
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
                checkUserRole();
            }

            @Override
            public void onError(Exception exception) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this,
                        "Login failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void signInWithGoogle() {
        progressBar.setVisibility(View.VISIBLE);
        startActivityForResult(googleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != RC_SIGN_IN) {
            return;
        }

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            accountRepository.loginWithGoogleToken(account.getIdToken(),
                    new AccountRepository.Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean isNewUser) {
                            if (Boolean.TRUE.equals(isNewUser)) {
                                showTermsAndConditions();
                            } else {
                                checkUserRole();
                            }
                        }

                        @Override
                        public void onError(Exception exception) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(LoginActivity.this,
                                    "Authentication failed", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (ApiException e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showTermsAndConditions() {
        progressBar.setVisibility(View.GONE);

        new AlertDialog.Builder(this)
                .setTitle("Terms and Conditions")
                .setMessage("Welcome to DQRA!\n\n"
                        + "By signing in with Google, you agree to our Terms of Service and Privacy Policy.\n\n"
                        + "We collect and use your basic profile information to provide access to our services.\n\n"
                        + "Do you agree to these terms?")
                .setPositiveButton("I Agree", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);
                    createNewGoogleUser();
                })
                .setNegativeButton("Decline", (dialog, which) -> {
                    accountRepository.signOut();
                    googleSignInClient.signOut();
                    Toast.makeText(LoginActivity.this,
                            "You must agree to the terms to use this app", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
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

    private void createNewGoogleUser() {
        accountRepository.createGoogleUser(new AccountRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                progressBar.setVisibility(View.GONE);
                Intent intent = new Intent(LoginActivity.this, ProfileCheckActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception exception) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "Failed to create user", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateThroughProfileCheck() {
        startActivity(new Intent(this, ProfileCheckActivity.class));
        finish();
    }
}
