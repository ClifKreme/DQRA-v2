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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.appname.dqra.maindashboards.UserDashboard;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.SignInButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText txtEmail, txtPassword;
    private ImageView imgTogglePassword; // ✅ NEW: Password visibility toggle
    private Button btnLogin;
    private TextView tRegister;
    private ProgressBar progressBar;
    private androidx.cardview.widget.CardView btnGoogle;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 100;

    private boolean isNewUser = false; // Track if user is new
    private boolean isPasswordVisible = false; // ✅ NEW: Track password visibility state

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        txtEmail = findViewById(R.id.txtEmail);
        txtPassword = findViewById(R.id.txtPassword);
        imgTogglePassword = findViewById(R.id.imgTogglePassword); // ✅ NEW
        btnLogin = findViewById(R.id.btnlogin);
        progressBar = findViewById(R.id.ProgressBar);
        tRegister = findViewById(R.id.txtregister);
        btnGoogle = findViewById(R.id.btnGoogle);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // ✅ NEW: Setup password visibility toggle
        setupPasswordToggle();

        // Email/password login
        btnLogin.setOnClickListener(v -> loginUser());

        // Register
        tRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        // Google Sign-In setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogle.setOnClickListener(v -> signInWithGoogle());
    }

    // ✅ NEW: Password visibility toggle functionality
    private void setupPasswordToggle() {
        if (imgTogglePassword != null) {
            imgTogglePassword.setOnClickListener(v -> {
                if (isPasswordVisible) {
                    // Hide password
                    txtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    imgTogglePassword.setImageResource(R.drawable.ic_visibility_off);
                    isPasswordVisible = false;
                } else {
                    // Show password
                    txtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    imgTogglePassword.setImageResource(R.drawable.ic_visibility);
                    isPasswordVisible = true;
                }
                // Move cursor to end of text
                txtPassword.setSelection(txtPassword.getText().length());
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // User is already logged in, go through ProfileCheckActivity
            Intent intent = new Intent(this, ProfileCheckActivity.class);
            startActivity(intent);
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

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        checkUserRole();
                    } else {
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ==================== GOOGLE SIGN-IN ====================
    private void signInWithGoogle() {
        progressBar.setVisibility(View.VISIBLE);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Check if this is a new user
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Check if user exists in Firestore
                            checkIfNewUser(user.getUid());
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ==================== CHECK IF NEW USER ====================
    private void checkIfNewUser(String uid) {
        db.collection("users").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (!task.getResult().exists()) {
                    // User doesn't exist in Firestore - show terms dialog
                    isNewUser = true;
                    showTermsAndConditions(uid);
                } else {
                    // Existing user - go through ProfileCheckActivity
                    isNewUser = false;
                    checkUserRole();
                }
            } else {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error checking user status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ==================== TERMS AND CONDITIONS ====================
    private void showTermsAndConditions(String uid) {
        progressBar.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Terms and Conditions");
        builder.setMessage(
                "Welcome to DQRA!\n\n" +
                        "By signing in with Google, you agree to our Terms of Service and Privacy Policy.\n\n" +
                        "We collect and use your basic profile information (name, email) to provide you with access to our services.\n\n" +
                        "Your data will be handled securely and in accordance with applicable data protection laws.\n\n" +
                        "Do you agree to these terms?"
        );

        builder.setPositiveButton("I Agree", (dialog, which) -> {
            dialog.dismiss();
            progressBar.setVisibility(View.VISIBLE);
            // Create new user account after accepting terms
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                createNewGoogleUser(user.getUid(), user.getEmail());
            }
        });

        builder.setNegativeButton("Decline", (dialog, which) -> {
            dialog.dismiss();
            // Sign out the user if they decline
            mAuth.signOut();
            googleSignInClient.signOut();
            Toast.makeText(LoginActivity.this, "You must agree to the terms to use this app", Toast.LENGTH_LONG).show();
        });

        builder.setCancelable(false); // Prevent dismissing by tapping outside

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // ==================== ROLE CHECKING ====================
    private void checkUserRole() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        progressBar.setVisibility(View.VISIBLE);

        db.collection("main").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                String role = task.getResult().getString("role");
                if ("main".equalsIgnoreCase(role)) {
                    navigateToDashboard(role);
                } else {
                    checkUsersCollection(uid);
                }
            } else {
                checkUsersCollection(uid);
            }
        });
    }

    private void checkUsersCollection(String uid) {
        db.collection("users").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                String role = task.getResult().getString("role");
                navigateToDashboard(role);
            } else {
                checkAdminRole(uid);
            }
        });
    }

    private void checkAdminRole(String uid) {
        db.collection("admins_with_stations").document(uid).get().addOnCompleteListener(task -> {
            progressBar.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult().exists()) {
                startActivity(new Intent(LoginActivity.this, AdminDashboard.class));
                finish();
            } else {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "User account not found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createNewGoogleUser(String uid, String email) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("role", "User");
        userMap.put("email", email);
        userMap.put("termsAccepted", true); // Track that terms were accepted
        userMap.put("createdAt", System.currentTimeMillis());
        // Note: fullName, phone, and address will be set in CompleteProfileActivity

        db.collection("users").document(uid).set(userMap)
                .addOnSuccessListener(unused -> {
                    progressBar.setVisibility(View.GONE);
                    // Redirect to ProfileCheckActivity which will check profile completion
                    Intent intent = new Intent(LoginActivity.this, ProfileCheckActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to create user", Toast.LENGTH_SHORT).show();
                });
    }

    private void navigateToDashboard(String role) {
        // Always go through ProfileCheckActivity to verify profile completion
        Intent intent = new Intent(this, ProfileCheckActivity.class);
        startActivity(intent);
        finish();
    }
}
