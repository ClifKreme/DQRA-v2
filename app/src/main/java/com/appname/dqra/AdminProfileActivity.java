package com.appname.dqra;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AdminProfileActivity extends BaseActivity {

    private static final int PROFILE_IMAGE_MAX_SIZE = 512;
    private static final int PROFILE_IMAGE_QUALITY = 70;
    private static final String ADMINS_COLLECTION = "admins_with_stations";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    // Header views
    private ImageView ivAdminProfileAvatar;
    private TextView tvAdminProfileTitle, tvAdminProfileSubtitle;

    // Info display TextViews
    private TextView tvAdminStationName, tvAdminMunicipality, tvAdminStationPhone, tvAdminEmail;

    // Info edit EditTexts
    private EditText etAdminStationName, etAdminMunicipality, etAdminStationPhone;

    // Action buttons
    private Button btnAdminSave, btnAdminCancel;
    private ImageButton btnAdminEdit, btnAdminMenu;

    private ActivityResultLauncher<String> pickProfileImageLauncher;
    private String currentProfileImageBase64;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_admin_profile);
        setActiveTab(Tab.PROFILE);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        setupImagePicker();
        initializeViews();
        setupListeners();
        loadAdminProfile();
    }

    @Override
    protected Class<?> getHomeActivityClass() {
        return AdminDashboard.class;
    }

    @Override
    protected Class<?> getProfileActivityClass() {
        return AdminProfileActivity.class;
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private void setupImagePicker() {
        pickProfileImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        saveSelectedProfileImage(uri);
                    }
                });
    }

    private void initializeViews() {
        ivAdminProfileAvatar = findViewById(R.id.ivAdminProfileAvatar);
        tvAdminProfileTitle   = findViewById(R.id.tvAdminProfileTitle);
        tvAdminProfileSubtitle = findViewById(R.id.tvAdminProfileSubtitle);

        tvAdminStationName  = findViewById(R.id.tvAdminStationName);
        tvAdminMunicipality = findViewById(R.id.tvAdminMunicipality);
        tvAdminStationPhone = findViewById(R.id.tvAdminStationPhone);
        tvAdminEmail        = findViewById(R.id.tvAdminEmail);

        etAdminStationName  = findViewById(R.id.etAdminStationName);
        etAdminMunicipality = findViewById(R.id.etAdminMunicipality);
        etAdminStationPhone = findViewById(R.id.etAdminStationPhone);

        btnAdminEdit   = findViewById(R.id.btnAdminEdit);
        btnAdminMenu   = findViewById(R.id.btnAdminMenu);
        btnAdminSave   = findViewById(R.id.btnAdminSave);
        btnAdminCancel = findViewById(R.id.btnAdminCancel);
    }

    private void setupListeners() {
        // Avatar — tap to view or change photo
        FrameLayout flAvatarContainer = findViewById(R.id.flAdminAvatarContainer);
        flAvatarContainer.setOnClickListener(v -> showProfilePhotoOptions());

        btnAdminEdit.setOnClickListener(v -> enterEditMode());
        btnAdminSave.setOnClickListener(v -> saveProfile());
        btnAdminCancel.setOnClickListener(v -> exitEditMode());
        btnAdminMenu.setOnClickListener(this::showBurgerMenu);
    }

    // ─── Burger menu ─────────────────────────────────────────────────────────

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Confirm", (dialog, which) -> logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBurgerMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.admin_profile_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.admin_action_emergency_contacts) {
                startActivity(new Intent(this, EmergencyContactsActivity.class));
                return true;
            } else if (id == R.id.admin_action_help) {
                startActivity(new Intent(this, HelpSupportActivity.class));
                return true;
            } else if (id == R.id.admin_action_about) {
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            } else if (id == R.id.admin_action_logout) {
                showLogoutConfirmation();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    // ─── Photo options ───────────────────────────────────────────────────────

    private void showProfilePhotoOptions() {
        boolean hasPhoto = currentProfileImageBase64 != null && !currentProfileImageBase64.trim().isEmpty();
        String[] options = hasPhoto
                ? new String[]{"View Photo", "Change Photo"}
                : new String[]{"Upload Photo"};

        new AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (hasPhoto && which == 0) {
                        viewFullScreenPhoto();
                    } else {
                        pickProfileImageLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void viewFullScreenPhoto() {
        Intent intent = new Intent(this, com.appname.dqra.image.FullScreenImageActivity.class);
        intent.putExtra("image_base64", currentProfileImageBase64);
        startActivity(intent);
    }

    // ─── Edit mode ───────────────────────────────────────────────────────────

    private void enterEditMode() {
        // Pre-fill EditTexts with current values
        etAdminStationName.setText(tvAdminStationName.getText());
        etAdminMunicipality.setText(tvAdminMunicipality.getText());
        etAdminStationPhone.setText(tvAdminStationPhone.getText());

        // Switch display → edit
        tvAdminStationName.setVisibility(View.GONE);
        tvAdminMunicipality.setVisibility(View.GONE);
        tvAdminStationPhone.setVisibility(View.GONE);

        etAdminStationName.setVisibility(View.VISIBLE);
        etAdminMunicipality.setVisibility(View.VISIBLE);
        etAdminStationPhone.setVisibility(View.VISIBLE);

        // Show Save/Cancel, hide Edit/Logout
        btnAdminSave.setVisibility(View.VISIBLE);
        btnAdminCancel.setVisibility(View.VISIBLE);
        btnAdminEdit.setVisibility(View.GONE);

        etAdminStationName.requestFocus();
    }

    private void exitEditMode() {
        // Switch edit → display
        etAdminStationName.setVisibility(View.GONE);
        etAdminMunicipality.setVisibility(View.GONE);
        etAdminStationPhone.setVisibility(View.GONE);

        tvAdminStationName.setVisibility(View.VISIBLE);
        tvAdminMunicipality.setVisibility(View.VISIBLE);
        tvAdminStationPhone.setVisibility(View.VISIBLE);

        // Restore buttons
        btnAdminSave.setVisibility(View.GONE);
        btnAdminCancel.setVisibility(View.GONE);
        btnAdminEdit.setVisibility(View.VISIBLE);
    }

    private void saveProfile() {
        String stationName  = etAdminStationName.getText().toString().trim();
        String municipality = etAdminMunicipality.getText().toString().trim();
        String stationPhone = etAdminStationPhone.getText().toString().trim();

        if (stationName.isEmpty() || municipality.isEmpty()) {
            Toast.makeText(this, "Station name and municipality cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
        if (userId == null) {
            goToLogin();
            return;
        }

        btnAdminSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("stationName", stationName);
        updates.put("municipality", municipality);
        updates.put("stationPhone", stationPhone);

        firestore.collection(ADMINS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    // Update display TextViews
                    tvAdminStationName.setText(stationName);
                    tvAdminMunicipality.setText(municipality);
                    tvAdminStationPhone.setText(stationPhone.isEmpty() ? "N/A" : stationPhone);
                    tvAdminProfileTitle.setText(stationName);
                    tvAdminProfileSubtitle.setText(municipality);

                    btnAdminSave.setEnabled(true);
                    exitEditMode();
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(exception -> {
                    btnAdminSave.setEnabled(true);
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                });
    }

    // ─── Load profile ────────────────────────────────────────────────────────

    private void loadAdminProfile() {
        String userId = auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
        if (userId == null) {
            goToLogin();
            return;
        }

        firestore.collection(ADMINS_COLLECTION).document(userId).get()
                .addOnSuccessListener(this::renderAdminProfile)
                .addOnFailureListener(exception ->
                        Toast.makeText(this, "Failed to load admin profile", Toast.LENGTH_SHORT).show());
    }

    private void renderAdminProfile(DocumentSnapshot document) {
        if (!document.exists()) {
            Toast.makeText(this, "Admin profile not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String stationName  = valueOrFallback(document.getString("stationName"));
        String municipality = valueOrFallback(document.getString("municipality"));
        String stationPhone = valueOrFallback(document.getString("stationPhone"));
        String email        = valueOrFallback(document.getString("email"));

        tvAdminProfileTitle.setText(stationName);
        tvAdminProfileSubtitle.setText(municipality);
        tvAdminStationName.setText(stationName);
        tvAdminMunicipality.setText(municipality);
        tvAdminStationPhone.setText(stationPhone);
        tvAdminEmail.setText(email);

        currentProfileImageBase64 = document.getString("profileImageBase64");
        renderProfileImage(currentProfileImageBase64);
    }

    private String valueOrFallback(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value;
    }

    // ─── Profile image ───────────────────────────────────────────────────────

    private void saveSelectedProfileImage(Uri uri) {
        try {
            String encodedImage = encodeProfileImage(uri);
            String userId = auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
            if (userId == null) {
                goToLogin();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("profileImageBase64", encodedImage);
            firestore.collection(ADMINS_COLLECTION).document(userId)
                    .update(updates)
                    .addOnSuccessListener(unused -> {
                        currentProfileImageBase64 = encodedImage;
                        renderProfileImage(encodedImage);
                        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(exception ->
                            Toast.makeText(this, "Photo update failed", Toast.LENGTH_SHORT).show());
        } catch (Exception exception) {
            Toast.makeText(this, "Failed to read selected image", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderProfileImage(String profileImageBase64) {
        ivAdminProfileAvatar.setClipToOutline(true);
        ivAdminProfileAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });

        if (profileImageBase64 == null || profileImageBase64.trim().isEmpty()) {
            ivAdminProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            return;
        }

        try {
            byte[] imageBytes = Base64.decode(profileImageBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap != null) {
                ivAdminProfileAvatar.setImageBitmap(bitmap);
            } else {
                ivAdminProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } catch (IllegalArgumentException exception) {
            ivAdminProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    private String encodeProfileImage(Uri uri) throws Exception {
        Bitmap originalBitmap;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            originalBitmap = BitmapFactory.decodeStream(inputStream);
        }

        if (originalBitmap == null) {
            throw new IllegalArgumentException("Selected file is not a valid image");
        }

        Bitmap orientedBitmap = applyImageOrientation(uri, originalBitmap);
        Bitmap scaledBitmap   = scaleBitmap(orientedBitmap);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, PROFILE_IMAGE_QUALITY, outputStream);

        if (scaledBitmap != orientedBitmap) orientedBitmap.recycle();
        if (orientedBitmap != originalBitmap) originalBitmap.recycle();

        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }

    private Bitmap applyImageOrientation(Uri uri, Bitmap bitmap) throws Exception {
        int orientation;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return bitmap;
            ExifInterface exifInterface = new ExifInterface(inputStream);
            orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:    matrix.postRotate(90);  break;
            case ExifInterface.ORIENTATION_ROTATE_180:   matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270:   matrix.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:   matrix.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270); matrix.postScale(-1, 1); break;
            default: return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap scaleBitmap(Bitmap bitmap) {
        int width  = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largestSide = Math.max(width, height);
        if (largestSide <= PROFILE_IMAGE_MAX_SIZE) return bitmap;
        float scale = PROFILE_IMAGE_MAX_SIZE / (float) largestSide;
        return Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true);
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    private void logout() {
        auth.signOut();
        goToLogin();
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
