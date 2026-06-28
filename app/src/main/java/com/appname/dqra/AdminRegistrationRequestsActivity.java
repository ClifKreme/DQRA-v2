package com.appname.dqra;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.UserRegistrationRequest;
import com.appname.dqra.repository.UserRegistrationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminRegistrationRequestsActivity extends BaseActivity {

    private UserRegistrationRepository repository;
    private UserRegistrationRequestAdapter adapter;
    private ListenerRegistration listenerRegistration;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_admin_registration_requests);
        setActiveTab(Tab.PROFILE);

        repository = new UserRegistrationRepository(this);
        initializeViews();
        listenForRequests();
    }

    @Override
    protected void onDestroy() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        super.onDestroy();
    }

    @Override
    protected Class<?> getHomeActivityClass() {
        return com.appname.dqra.maindashboards.AdminDashboard.class;
    }

    @Override
    protected Class<?> getProfileActivityClass() {
        return AdminProfileActivity.class;
    }

    private void initializeViews() {
        ImageButton btnBack = findViewById(R.id.btnRequestsBack);
        RecyclerView rvRequests = findViewById(R.id.rvRegistrationRequests);
        progressBar = findViewById(R.id.requestsProgressBar);
        emptyState = findViewById(R.id.tvRequestsEmpty);
        tvSubtitle = findViewById(R.id.tvRequestsSubtitle);

        btnBack.setOnClickListener(v -> finish());
        adapter = new UserRegistrationRequestAdapter(this::showRequestDetails);
        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        rvRequests.setAdapter(adapter);
    }

    private void listenForRequests() {
        progressBar.setVisibility(View.VISIBLE);
        listenerRegistration = repository.listenForPendingRequests(
                new UserRegistrationRepository.RequestListener() {
                    @Override
                    public void onRequestsChanged(java.util.List<UserRegistrationRequest> requests) {
                        progressBar.setVisibility(View.GONE);
                        adapter.submitList(requests);
                        boolean empty = requests.isEmpty();
                        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                        tvSubtitle.setText(empty
                                ? "No pending requests"
                                : requests.size() + " pending approval");
                    }

                    @Override
                    public void onError(Exception exception) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(AdminRegistrationRequestsActivity.this,
                                "Failed to load requests: " + exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showRequestDetails(UserRegistrationRequest request) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_registration_request_detail, null);

        // Populate text fields
        bindText(dialogView, R.id.tvDetailName,    request.getFullName());
        bindText(dialogView, R.id.tvDetailEmail,   request.getEmail());
        bindText(dialogView, R.id.tvDetailPhone,   request.getPhone());
        bindText(dialogView, R.id.tvDetailAddress, request.getAddress());

        // Populate images
        bindImage(dialogView, R.id.ivDetailSelfie,          request.getSelfieImageBase64());
        bindImage(dialogView, R.id.ivDetailNationalIdFront,  request.getNationalIdFrontBase64());
        bindImage(dialogView, R.id.ivDetailNationalIdBack,   request.getNationalIdBackBase64());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Wire action buttons inside the layout
        dialogView.findViewById(R.id.btnDetailApprove).setOnClickListener(v -> {
            dialog.dismiss();
            confirmApprove(request);
        });
        dialogView.findViewById(R.id.btnDetailReject).setOnClickListener(v -> {
            dialog.dismiss();
            confirmReject(request);
        });

        dialog.show();
    }

    // ── Detail helpers ────────────────────────────────────────────────────────

    private void bindText(View root, int viewId, String value) {
        ((TextView) root.findViewById(viewId)).setText(valueOrFallback(value));
    }

    private void bindImage(View root, int viewId, String base64) {
        ImageView imageView = root.findViewById(viewId);
        Bitmap bitmap = decodeImage(base64);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            imageView.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.appname.dqra.image.FullScreenImageActivity.class);
                intent.putExtra("image_base64", base64);
                startActivity(intent);
            });
        }
    }

    // ── Approve / Reject ──────────────────────────────────────────────────────

    private void confirmApprove(UserRegistrationRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("Approve Registration")
                .setMessage("Approve " + valueOrFallback(request.getFullName()) + "?")
                .setPositiveButton("Approve", (d, w) -> approveRequest(request))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmReject(UserRegistrationRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("Reject Registration")
                .setMessage("Reject " + valueOrFallback(request.getFullName()) + "? This cannot be undone.")
                .setPositiveButton("Reject", (d, w) -> rejectRequest(request))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void approveRequest(UserRegistrationRequest request) {
        repository.approveRequest(request, getCurrentAdminId(),
                new UserRegistrationRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Toast.makeText(AdminRegistrationRequestsActivity.this,
                                "Registration approved", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(AdminRegistrationRequestsActivity.this,
                                "Approval failed: " + exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void rejectRequest(UserRegistrationRequest request) {
        repository.rejectRequest(request, getCurrentAdminId(),
                new UserRegistrationRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Toast.makeText(AdminRegistrationRequestsActivity.this,
                                "Registration rejected", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Toast.makeText(AdminRegistrationRequestsActivity.this,
                                "Rejection failed: " + exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private Bitmap decodeImage(String imageBase64) {
        if (imageBase64 == null || imageBase64.trim().isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getCurrentAdminId() {
        return FirebaseAuth.getInstance().getCurrentUser() == null
                ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private String valueOrFallback(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value;
    }
}
