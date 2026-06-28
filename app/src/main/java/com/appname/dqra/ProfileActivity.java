package com.appname.dqra;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
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
import androidx.lifecycle.ViewModelProvider;

import com.appname.dqra.model.UserProfile;
import com.appname.dqra.viewmodel.OperationState;
import com.appname.dqra.viewmodel.ProfileViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileActivity extends BaseActivity {

    private static final int PROFILE_IMAGE_MAX_SIZE = 512;
    private static final int PROFILE_IMAGE_QUALITY = 70;

    // Header
    private ImageView ivProfileAvatar;
    private TextView tvProfileTitle;

    // Info display
    private TextView tvFullName, tvPhone, tvAddress;

    // Info edit
    private EditText etFullName, etPhone, etAddress;

    // Buttons
    private ImageButton btnEdit, btnMenu;
    private Button btnSave, btnCancel;

    private GoogleSignInClient googleSignInClient;
    private ProfileViewModel viewModel;
    private ActivityResultLauncher<String> pickProfileImageLauncher;
    private String currentProfileImageBase64;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_profile);
        setActiveTab(Tab.PROFILE);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        setupImagePicker();
        initializeViews();
        observeViewModel();
        setupListeners();
        viewModel.loadProfile();
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private void setupImagePicker() {
        pickProfileImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) saveSelectedProfileImage(uri);
                });
    }

    private void initializeViews() {
        ivProfileAvatar = findViewById(R.id.ivProfileAvatar);
        tvProfileTitle  = findViewById(R.id.tvProfileTitle);

        tvFullName = findViewById(R.id.FullName);
        tvPhone    = findViewById(R.id.vPhone);
        tvAddress  = findViewById(R.id.vAddress);

        etFullName = findViewById(R.id.atFullName);
        etPhone    = findViewById(R.id.tPhone);
        etAddress  = findViewById(R.id.tAddress);

        btnEdit   = findViewById(R.id.BtnEdit);
        btnMenu   = findViewById(R.id.BtnMenu);
        btnSave   = findViewById(R.id.BtnSave);
        btnCancel = findViewById(R.id.BtnCancel);
    }

    private void observeViewModel() {
        viewModel.getProfile().observe(this, this::renderProfile);
        viewModel.getState().observe(this, state -> {
            if (state == null) return;

            boolean loading = state.getStatus() == OperationState.Status.LOADING;
            btnSave.setEnabled(!loading);

            if (state.getStatus() == OperationState.Status.SUCCESS) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                exitEditMode();
            } else if (state.getStatus() == OperationState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupListeners() {
        // Avatar container — view or change photo
        FrameLayout flAvatarContainer = findViewById(R.id.flProfileAvatarContainer);
        flAvatarContainer.setOnClickListener(v -> showProfilePhotoOptions());

        btnEdit.setOnClickListener(v -> {
            long daysLeft = viewModel.getDaysUntilNextUpdate();
            if (daysLeft > 0) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Update Not Allowed")
                        .setMessage("You can only update your profile once per month.\n"
                                + "Please wait " + daysLeft + " more day(s).")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            enterEditMode();
        });
        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener(v -> exitEditMode());
        btnMenu.setOnClickListener(this::showMenu);
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

    private void showMenu(View anchor) {        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.profile_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_station_websites) {
                startActivity(new Intent(this, WebsiteActivity.class));
                return true;
            } else if (id == R.id.action_emergency_contacts) {
                startActivity(new Intent(this, EmergencyContactsActivity.class));
                return true;
            } else if (id == R.id.action_help) {
                startActivity(new Intent(this, HelpSupportActivity.class));
                return true;
            } else if (id == R.id.action_about) {
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            } else if (id == R.id.action_logout) {
                showLogoutConfirmation();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    // ─── Photo options ───────────────────────────────────────────────────────

    private void showProfilePhotoOptions() {
        boolean hasPhoto = currentProfileImageBase64 != null
                && !currentProfileImageBase64.trim().isEmpty();

        // Check photo cooldown before offering "Change Photo" or "Upload Photo"
        long daysLeft = viewModel.getDaysUntilNextPhotoUpdate();
        if (daysLeft > 0) {
            if (hasPhoto) {
                // Still let them view, but block changing
                new AlertDialog.Builder(this)
                        .setTitle("Profile Photo")
                        .setItems(new String[]{"View Photo"}, (dialog, which) -> viewFullScreenPhoto())
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Photo Change Not Allowed")
                        .setMessage("You can only change your profile photo once per month.\n"
                                + "Please wait " + daysLeft + " more day(s).")
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }

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
        etFullName.setText(tvFullName.getText());
        etPhone.setText(tvPhone.getText());
        etAddress.setText(tvAddress.getText());

        tvFullName.setVisibility(View.GONE);
        tvPhone.setVisibility(View.GONE);
        tvAddress.setVisibility(View.GONE);

        etFullName.setVisibility(View.VISIBLE);
        etPhone.setVisibility(View.VISIBLE);
        etAddress.setVisibility(View.VISIBLE);

        btnSave.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
        btnEdit.setVisibility(View.GONE);

        etFullName.requestFocus();
    }

    private void exitEditMode() {
        etFullName.setVisibility(View.GONE);
        etPhone.setVisibility(View.GONE);
        etAddress.setVisibility(View.GONE);

        tvFullName.setVisibility(View.VISIBLE);
        tvPhone.setVisibility(View.VISIBLE);
        tvAddress.setVisibility(View.VISIBLE);

        btnSave.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);
        btnEdit.setVisibility(View.VISIBLE);
    }

    private void saveProfile() {
        String fullName = etFullName.getText().toString().trim();
        String phone    = etPhone.getText().toString().trim();
        String address  = etAddress.getText().toString().trim();
        viewModel.saveProfile(fullName, phone, address);
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    private void renderProfile(UserProfile profile) {
        if (profile == null) return;

        String name = profile.getFullName() != null ? profile.getFullName() : "N/A";
        tvProfileTitle.setText(name);
        tvFullName.setText(name);
        tvPhone.setText(profile.getPhone() != null ? profile.getPhone() : "N/A");
        tvAddress.setText(profile.getAddress() != null ? profile.getAddress() : "N/A");

        currentProfileImageBase64 = profile.getProfileImageBase64();
        renderProfileImage(currentProfileImageBase64);
    }

    private void renderProfileImage(String profileImageBase64) {
        ivProfileAvatar.setClipToOutline(true);
        ivProfileAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });

        if (profileImageBase64 == null || profileImageBase64.trim().isEmpty()) {
            ivProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            return;
        }

        try {
            byte[] imageBytes = Base64.decode(profileImageBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap != null) {
                ivProfileAvatar.setImageBitmap(bitmap);
            } else {
                ivProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        } catch (IllegalArgumentException exception) {
            ivProfileAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    // ─── Profile image save ──────────────────────────────────────────────────

    private void saveSelectedProfileImage(Uri uri) {
        try {
            String encodedImage = encodeProfileImage(uri);
            viewModel.saveProfileImage(encodedImage);
        } catch (Exception exception) {
            Toast.makeText(this, "Failed to read selected image", Toast.LENGTH_SHORT).show();
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

        Bitmap scaledBitmap = scaleBitmap(originalBitmap);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, PROFILE_IMAGE_QUALITY, outputStream);

        if (scaledBitmap != originalBitmap) originalBitmap.recycle();

        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
    }

    private Bitmap scaleBitmap(Bitmap bitmap) {
        int width  = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largestSide = Math.max(width, height);
        if (largestSide <= PROFILE_IMAGE_MAX_SIZE) return bitmap;
        float scale = PROFILE_IMAGE_MAX_SIZE / (float) largestSide;
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(width * scale), Math.round(height * scale), true);
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    private void logout() {
        viewModel.signOut();
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Toast.makeText(this, "Successfully logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
    }
}
