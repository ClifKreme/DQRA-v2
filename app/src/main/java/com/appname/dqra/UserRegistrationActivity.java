package com.appname.dqra;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.appname.dqra.repository.UserRegistrationRepository;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class UserRegistrationActivity extends AppCompatActivity {

    private static final int IMAGE_MAX_SIZE = 900;
    private static final int IMAGE_QUALITY = 75;
    private static final String REGISTRATION_RATE_LIMIT_PREFS = "registration_rate_limit";
    private static final String KEY_NEXT_SUBMIT_AT = "next_submit_at";
    private static final long REGISTRATION_COOLDOWN_MS = 5 * 60 * 1000L;

    // Target constants for camera/picker routing
    private static final int TARGET_SELFIE           = 0;
    private static final int TARGET_ID_FRONT         = 1;
    private static final int TARGET_ID_BACK          = 2;

    // Views
    private EditText etFullName, etEmail, etPassword, etConfirmPassword, etPhone, etAddress;
    private ImageView imgTogglePassword, imgToggleConfirmPassword;

    private ImageView ivSelfiePreview;
    private LinearLayout layoutSelfiePlaceholder;

    private ImageView ivNationalIdFrontPreview;
    private LinearLayout layoutIdFrontPlaceholder;

    private ImageView ivNationalIdBackPreview;
    private LinearLayout layoutIdBackPlaceholder;

    private Button btnUploadSelfie, btnCaptureSelfie;
    private Button btnUploadNationalIdFront, btnCaptureNationalIdFront;
    private Button btnUploadNationalIdBack, btnCaptureNationalIdBack;
    private Button btnSubmitRegistration;
    private ProgressBar progressBar;

    // State
    private boolean isPasswordVisible        = false;
    private boolean isConfirmPasswordVisible = false;

    private String selfieBase64;
    private String nationalIdFrontBase64;
    private String nationalIdBackBase64;

    private int currentTarget = -1;
    private Uri cameraCaptureUri;

    // Result launchers
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Uri>    cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    private UserRegistrationRepository repository;
    private SharedPreferences rateLimitPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_registration);

        repository = new UserRegistrationRepository(this);
        rateLimitPrefs = getSharedPreferences(REGISTRATION_RATE_LIMIT_PREFS, MODE_PRIVATE);
        setupLaunchers();
        initViews();
        setupListeners();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) processUri(uri, currentTarget);
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && cameraCaptureUri != null) {
                        processUri(cameraCaptureUri, currentTarget);
                    }
                });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        launchCamera();
                    } else {
                        Toast.makeText(this,
                                "Camera permission is needed to capture photos",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void initViews() {
        etFullName              = findViewById(R.id.etRegisterFullName);
        etEmail                 = findViewById(R.id.etRegisterEmail);
        etPassword              = findViewById(R.id.etRegisterPassword);
        etConfirmPassword       = findViewById(R.id.etRegisterConfirmPassword);
        etPhone                 = findViewById(R.id.etRegisterPhone);
        etAddress               = findViewById(R.id.etRegisterAddress);

        imgTogglePassword        = findViewById(R.id.imgTogglePassword);
        imgToggleConfirmPassword = findViewById(R.id.imgToggleConfirmPassword);

        ivSelfiePreview          = findViewById(R.id.ivSelfiePreview);
        layoutSelfiePlaceholder  = findViewById(R.id.layoutSelfiePlaceholder);

        ivNationalIdFrontPreview = findViewById(R.id.ivNationalIdFrontPreview);
        layoutIdFrontPlaceholder = findViewById(R.id.layoutIdFrontPlaceholder);

        ivNationalIdBackPreview  = findViewById(R.id.ivNationalIdBackPreview);
        layoutIdBackPlaceholder  = findViewById(R.id.layoutIdBackPlaceholder);

        btnUploadSelfie          = findViewById(R.id.btnUploadSelfie);
        btnCaptureSelfie         = findViewById(R.id.btnCaptureSelfie);
        btnUploadNationalIdFront = findViewById(R.id.btnUploadNationalIdFront);
        btnCaptureNationalIdFront= findViewById(R.id.btnCaptureNationalIdFront);
        btnUploadNationalIdBack  = findViewById(R.id.btnUploadNationalIdBack);
        btnCaptureNationalIdBack = findViewById(R.id.btnCaptureNationalIdBack);
        btnSubmitRegistration    = findViewById(R.id.btnSubmitRegistration);
        progressBar              = findViewById(R.id.registrationProgressBar);

        // Ensure passwords start hidden
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private void setupListeners() {
        // Password visibility toggles
        imgTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            togglePasswordVisibility(etPassword, imgTogglePassword, isPasswordVisible);
        });
        imgToggleConfirmPassword.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            togglePasswordVisibility(etConfirmPassword, imgToggleConfirmPassword, isConfirmPasswordVisible);
        });

        // Selfie
        btnUploadSelfie.setOnClickListener(v -> openGallery(TARGET_SELFIE));
        btnCaptureSelfie.setOnClickListener(v -> openCamera(TARGET_SELFIE));

        // National ID front
        btnUploadNationalIdFront.setOnClickListener(v -> openGallery(TARGET_ID_FRONT));
        btnCaptureNationalIdFront.setOnClickListener(v -> openCamera(TARGET_ID_FRONT));

        // National ID back
        btnUploadNationalIdBack.setOnClickListener(v -> openGallery(TARGET_ID_BACK));
        btnCaptureNationalIdBack.setOnClickListener(v -> openCamera(TARGET_ID_BACK));

        btnSubmitRegistration.setOnClickListener(v -> submitRegistration());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Password Toggle
    // ─────────────────────────────────────────────────────────────────────────

    private void togglePasswordVisibility(EditText field, ImageView toggle, boolean visible) {
        if (visible) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggle.setImageResource(R.drawable.ic_visibility);
        } else {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggle.setImageResource(R.drawable.ic_visibility_off);
        }
        // Keep cursor at end
        field.setSelection(field.getText().length());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Image Picking
    // ─────────────────────────────────────────────────────────────────────────

    private void openGallery(int target) {
        currentTarget = target;
        galleryLauncher.launch("image/*");
    }

    private void openCamera(int target) {
        currentTarget = target;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            File photoFile = createTempImageFile();
            cameraCaptureUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile);
            cameraLauncher.launch(cameraCaptureUri);
        } catch (IOException e) {
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        }
    }

    private File createTempImageFile() throws IOException {
        File storageDir = getCacheDir();
        return File.createTempFile("reg_capture_", ".jpg", storageDir);
    }

    private void processUri(Uri uri, int target) {
        try {
            String encoded = encodeImage(uri);
            Bitmap preview = decodePreview(encoded);
            switch (target) {
                case TARGET_SELFIE:
                    selfieBase64 = encoded;
                    showPreview(ivSelfiePreview, layoutSelfiePlaceholder, preview);
                    break;
                case TARGET_ID_FRONT:
                    nationalIdFrontBase64 = encoded;
                    showPreview(ivNationalIdFrontPreview, layoutIdFrontPlaceholder, preview);
                    break;
                case TARGET_ID_BACK:
                    nationalIdBackBase64 = encoded;
                    showPreview(ivNationalIdBackPreview, layoutIdBackPlaceholder, preview);
                    break;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read selected image", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreview(ImageView previewView, LinearLayout placeholder, Bitmap bitmap) {
        previewView.setImageBitmap(bitmap);
        previewView.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Submission
    // ─────────────────────────────────────────────────────────────────────────

    private void submitRegistration() {
        if (isRegistrationOnCooldown()) {
            Toast.makeText(this, registrationCooldownMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        String fullName  = etFullName.getText().toString().trim();
        String email     = etEmail.getText().toString().trim();
        String password  = etPassword.getText().toString().trim();
        String confirm   = etConfirmPassword.getText().toString().trim();
        String phone     = etPhone.getText().toString().trim();
        String address   = etAddress.getText().toString().trim();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()
                || phone.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Please complete all personal details", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }
        if (selfieBase64 == null) {
            Toast.makeText(this, "Please provide your selfie photo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nationalIdFrontBase64 == null) {
            Toast.makeText(this, "Please provide the front of your national ID", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nationalIdBackBase64 == null) {
            Toast.makeText(this, "Please provide the back of your national ID", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        repository.submitRegistration(fullName, email, password, phone, address,
                selfieBase64, nationalIdFrontBase64, nationalIdBackBase64,
                new UserRegistrationRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        setLoading(false);
                        startRegistrationCooldown();
                        new AlertDialog.Builder(UserRegistrationActivity.this)
                                .setTitle("Registration Submitted")
                                .setMessage("Please wait while an admin verifies your details and ID. "
                                        + "This screen will continue automatically when your registration is approved.")
                                .setPositiveButton("OK", (dialog, which) -> {
                                    Intent intent = new Intent(UserRegistrationActivity.this,
                                            PendingApprovalActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .setCancelable(false)
                                .show();
                    }

                    @Override
                    public void onError(Exception exception) {
                        setLoading(false);
                        Toast.makeText(UserRegistrationActivity.this,
                                "Registration failed: " + exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean isRegistrationOnCooldown() {
        long nextSubmitAt = rateLimitPrefs.getLong(KEY_NEXT_SUBMIT_AT, 0L);
        if (nextSubmitAt <= System.currentTimeMillis()) {
            if (nextSubmitAt > 0L) {
                rateLimitPrefs.edit().remove(KEY_NEXT_SUBMIT_AT).apply();
            }
            return false;
        }
        return true;
    }

    private void startRegistrationCooldown() {
        rateLimitPrefs.edit()
                .putLong(KEY_NEXT_SUBMIT_AT, System.currentTimeMillis() + REGISTRATION_COOLDOWN_MS)
                .apply();
    }

    private String registrationCooldownMessage() {
        long remainingMs = Math.max(0L,
                rateLimitPrefs.getLong(KEY_NEXT_SUBMIT_AT, 0L) - System.currentTimeMillis());
        long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        return "Please wait " + seconds + " second(s) before submitting again.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Loading state
    // ─────────────────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmitRegistration.setEnabled(!loading);
        btnUploadSelfie.setEnabled(!loading);
        btnCaptureSelfie.setEnabled(!loading);
        btnUploadNationalIdFront.setEnabled(!loading);
        btnCaptureNationalIdFront.setEnabled(!loading);
        btnUploadNationalIdBack.setEnabled(!loading);
        btnCaptureNationalIdBack.setEnabled(!loading);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Image encoding helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Bitmap decodePreview(String base64) {
        byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private String encodeImage(Uri uri) throws Exception {
        Bitmap original;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            original = BitmapFactory.decodeStream(inputStream);
        }
        if (original == null) throw new IllegalArgumentException("Selected file is not a valid image");

        Bitmap oriented = applyOrientation(uri, original);
        Bitmap scaled   = scaleBitmap(oriented);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, out);

        if (scaled   != oriented) oriented.recycle();
        if (oriented != original) original.recycle();

        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.DEFAULT);
    }

    private Bitmap applyOrientation(Uri uri, Bitmap bitmap) throws Exception {
        int orientation;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return bitmap;
            ExifInterface exif = new ExifInterface(inputStream);
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  matrix.postRotate(90);  break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            default: return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap scaleBitmap(Bitmap bitmap) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= IMAGE_MAX_SIZE) return bitmap;
        float scale = IMAGE_MAX_SIZE / (float) largest;
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(bitmap.getWidth()  * scale),
                Math.round(bitmap.getHeight() * scale),
                true);
    }
}
