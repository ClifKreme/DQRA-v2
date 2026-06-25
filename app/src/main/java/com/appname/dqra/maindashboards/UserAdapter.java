package com.appname.dqra.maindashboards;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.R;
import com.appname.dqra.model.RescueStation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private Context context;
    private List<RescueStation> rescueStationList;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    private static final int LOCATION_PERMISSION_REQUEST_CODE  = 1001;
    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1002;
    private static final int CAMERA_PERMISSION_REQUEST_CODE     = 1003;
    private static final int REQUEST_IMAGE_CAPTURE              = 1004;

    private String currentEmergencyDocId;

    // User location for distance calculation
    private double userLatitude  = 0;
    private double userLongitude = 0;

    // Auto-refresh
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 500;
    private boolean isRefreshing = false;

    public UserAdapter(Context context, List<RescueStation> rescueStationList) {
        this.context = context;
        this.rescueStationList = rescueStationList;
        this.db = FirebaseFirestore.getInstance();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        startAutoRefresh();
    }

    // ─── Auto-refresh ─────────────────────────────────────────────────────────

    public void startAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRefreshing) {
                    isRefreshing = true;
                    notifyDataSetChanged();
                    isRefreshing = false;
                }
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    public void stopAutoRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    // ─── User location ────────────────────────────────────────────────────────

    public void setUserLocation(double lat, double lon) {
        this.userLatitude  = lat;
        this.userLongitude = lon;
        notifyDataSetChanged();
    }

    // ─── RecyclerView boilerplate ─────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.rescuers_stations, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RescueStation station = rescueStationList.get(position);

        // Station name
        String displayName = station.getStationName() != null
                ? station.getStationName() : station.getName();
        holder.tvStationName.setText(displayName);

        // Municipality + distance
        if (userLatitude != 0 && userLongitude != 0
                && station.getLatitude() != 0 && station.getLongitude() != 0) {
            double distance = calculateDistance(userLatitude, userLongitude,
                    station.getLatitude(), station.getLongitude());
            holder.tvMunicipality.setText(station.getMunicipality()
                    + " • " + String.format("%.1f km away", distance));
        } else {
            holder.tvMunicipality.setText(station.getMunicipality());
        }

        // ── Phone numbers ──────────────────────────────────────────────────────
        String phone1 = station.getPhoneNumber();
        String phone2 = station.getPhoneNumber2();

        if (phone2 != null && !phone2.trim().isEmpty()) {
            // Show both numbers on separate lines
            holder.tvPhoneNumber.setText(phone1 + "\n" + phone2);
        } else {
            holder.tvPhoneNumber.setText(phone1);
        }

        // ── Messenger button ───────────────────────────────────────────────────
        holder.btnMessenger.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                showEnableNetworkDialog(station);
            } else {
                getLocationAndShareMessage(station);
            }
        });

        // ── Emergency / Call button (online vs offline) ────────────────────────
        if (isNetworkAvailable()) {
            holder.btnAction.setText("🚨 SEND EMERGENCY ALERT");
            holder.btnAction.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFD32F2F));
            holder.btnAction.setOnClickListener(v ->
                    checkLocationPermissions(station.getId(), station.getMunicipality()));
        } else {
            holder.btnAction.setText("📞 CALL RESCUE STATION");
            holder.btnAction.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF2E7D32));
            holder.btnAction.setOnClickListener(v ->
                    showCallOptionsDialog(station));  // ← shows both numbers if available
        }
    }

    @Override
    public int getItemCount() {
        return rescueStationList.size();
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvStationName;
        public TextView tvMunicipality;
        public TextView tvPhoneNumber;
        public Button   btnAction;
        public Button   btnMessenger;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStationName  = itemView.findViewById(R.id.stationName);
            tvMunicipality = itemView.findViewById(R.id.tvMunicipality);
            tvPhoneNumber  = itemView.findViewById(R.id.stationPhone);
            btnAction      = itemView.findViewById(R.id.btnAction);
            btnMessenger   = itemView.findViewById(R.id.btnMessenger);
        }
    }

    // ─── Distance helper ──────────────────────────────────────────────────────

    private double calculateDistance(double userLat, double userLon,
                                     double stationLat, double stationLon) {
        final int R = 6371;
        double latDistance = Math.toRadians(stationLat - userLat);
        double lonDistance = Math.toRadians(stationLon - userLon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(stationLat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ─── Network helper ───────────────────────────────────────────────────────

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
        return false;
    }

    // ─── Call options (supports 2 numbers) ───────────────────────────────────

    /**
     * If the station has two phone numbers, let the user pick which one to call.
     * If only one number, call it directly.
     */
    private void showCallOptionsDialog(RescueStation station) {
        String phone1 = station.getPhoneNumber();
        String phone2 = station.getPhoneNumber2();

        boolean hasPhone2 = phone2 != null && !phone2.trim().isEmpty();

        if (!hasPhone2) {
            // Only one number — call immediately
            makePhoneCall(phone1);
            return;
        }

        // Two numbers — ask user which to call
        new AlertDialog.Builder(context)
                .setTitle("📞 Call Rescue Station")
                .setMessage("Select a number to call:")
                .setPositiveButton("📞 " + phone1, (dialog, which) -> makePhoneCall(phone1))
                .setNeutralButton("📞 " + phone2,  (dialog, which) -> makePhoneCall(phone2))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Make phone call ──────────────────────────────────────────────────────

    private void makePhoneCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.CALL_PHONE},
                    CALL_PHONE_PERMISSION_REQUEST_CODE);
            Toast.makeText(context, "Phone call permission needed for emergency calls",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            context.startActivity(callIntent);
        } catch (Exception e) {
            Toast.makeText(context, "Unable to make call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("UserAdapter", "Error making call: " + e.getMessage());
        }
    }

    // ─── No-network dialog ────────────────────────────────────────────────────

    private void showEnableNetworkDialog(RescueStation station) {
        new AlertDialog.Builder(context)
                .setTitle("⚠️ No Internet Connection")
                .setMessage("To share your location with the rescue station, you need an active " +
                        "internet connection.\n\nPlease enable Mobile Data or WiFi to continue.\n\n" +
                        "Would you like to open network settings?")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        context.startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                        Toast.makeText(context,
                                "Enable Mobile Data or WiFi, then return to the app and try again",
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        try {
                            context.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                        } catch (Exception ex) {
                            Toast.makeText(context,
                                    "Could not open settings. Please enable Mobile Data manually.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) ->
                        Toast.makeText(context, "Location sharing requires internet connection",
                                Toast.LENGTH_SHORT).show())
                .setNeutralButton("Try Anyway", (dialog, which) -> {
                    Toast.makeText(context,
                            "⚠️ No internet detected. Please enable Mobile Data before sending.",
                            Toast.LENGTH_LONG).show();
                    getLocationAndShareMessage(station);
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .show();
    }

    // ─── Messenger / Share ────────────────────────────────────────────────────

    private void getLocationAndShareMessage(RescueStation station) {
        String displayName = station.getStationName() != null
                ? station.getStationName() : station.getName();
        String municipality = station.getMunicipality();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            fetchUserDetailsAndShare(displayName, municipality, 0, 0);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    double lat = 0, lon = 0;
                    if (location != null) {
                        lat = location.getLatitude();
                        lon = location.getLongitude();
                    }
                    fetchUserDetailsAndShare(displayName, municipality, lat, lon);
                })
                .addOnFailureListener(e ->
                        fetchUserDetailsAndShare(displayName, municipality, 0, 0));
    }

    private void fetchUserDetailsAndShare(String stationName, String stationLocation,
                                          double lat, double lon) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showShareSheet(createLocationMessage(null, null, stationName, lat, lon),
                    stationName, stationLocation);
            return;
        }

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String fullName    = doc.exists() ? doc.getString("fullName") : null;
                    String phoneNumber = doc.exists() ? doc.getString("phone")    : null;
                    showShareSheet(createLocationMessage(fullName, phoneNumber, stationName, lat, lon),
                            stationName, stationLocation);
                })
                .addOnFailureListener(e ->
                        showShareSheet(createLocationMessage(null, null, stationName, lat, lon),
                                stationName, stationLocation));
    }

    private void showShareSheet(String message, String stationName, String stationLocation) {
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Emergency Message", message));
            showMessengerInstructions(stationName, stationLocation, message);
        } catch (Exception e) {
            Toast.makeText(context, "Error preparing message: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showMessengerInstructions(String stationName, String stationLocation, String message) {
        String rawText =
                "INSTRUCTIONS:\n\n" +
                        "🚨 EMERGENCY STATION:\n" + stationName + "\n" + stationLocation + "\n\n" +
                        "1. Click 'Continue' below\n" +
                        "2. Select 'Messenger' (or your preferred app)\n" +
                        "3. Search for the station above\n" +
                        "4. Send the emergency message\n\n" +
                        "Your emergency message with location has been copied and is ready to send!";

        SpannableString spannable = new SpannableString(rawText);

        int stationStart = rawText.indexOf(stationName);
        int stationEnd   = stationStart + stationName.length();
        spannable.setSpan(new StyleSpan(Typeface.BOLD), stationStart, stationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new RelativeSizeSpan(1.4f),   stationStart, stationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.RED), stationStart, stationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        int locationStart = rawText.indexOf(stationLocation);
        if (locationStart != -1) {
            int locationEnd = locationStart + stationLocation.length();
            spannable.setSpan(new StyleSpan(Typeface.BOLD), locationStart, locationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new RelativeSizeSpan(1.15f), locationStart, locationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#FF6B00")), locationStart, locationEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        new AlertDialog.Builder(context)
                .setTitle("📱 Send Emergency Message")
                .setMessage(spannable)
                .setPositiveButton("Continue", (dialog, which) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "🚨 EMERGENCY - " + stationName);
                    context.startActivity(Intent.createChooser(shareIntent, "Select Messenger or messaging app"));
                })
                .setNegativeButton("Cancel", (dialog, which) ->
                        Toast.makeText(context, "Message cancelled", Toast.LENGTH_SHORT).show())
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    private String createLocationMessage(String fullName, String phoneNumber,
                                         String stationName, double lat, double lon) {
        StringBuilder message = new StringBuilder();
        if (lat != 0 && lon != 0) {
            message.append("LOC: ").append(lat).append(", ").append(lon).append("\n\n");
        }
        message.append("🚨 EMERGENCY! Need assistance from ").append(stationName).append("!\n\n");
        if (fullName != null && !fullName.isEmpty())
            message.append("Name: ").append(fullName).append("\n");
        if (phoneNumber != null && !phoneNumber.isEmpty())
            message.append("Phone: ").append(phoneNumber);
        return message.toString();
    }

    // ─── Emergency alert flow ─────────────────────────────────────────────────

    private void checkLocationPermissions(String adminId, String municipality) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fetchUserLocation(adminId, municipality);
        }
    }

    private void fetchUserLocation(String adminId, String municipality) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    db.collection("users").document(currentUser.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    notifyStationAndRescuers(adminId, municipality, lat, lon,
                                            doc.getString("fullName"), doc.getString("phone"));
                                } else {
                                    Toast.makeText(context, "User data not found!", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed to fetch user data: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show());
                }
            } else {
                Toast.makeText(context, "Failed to fetch location!", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(context, "Error fetching location: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private void notifyStationAndRescuers(String adminId, String municipality,
                                          double userLat, double userLon,
                                          String fullName, String phoneNumber) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(context, "You must be logged in to send a request.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("emergency_locations")
                .whereEqualTo("reportedBy", fullName)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Toast.makeText(context, "You already have a pending emergency request!",
                                Toast.LENGTH_LONG).show();
                    } else {
                        createEmergencyRequest(adminId, municipality, userLat, userLon,
                                fullName, phoneNumber, currentUser.getUid());
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Error checking requests: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void createEmergencyRequest(String adminId, String municipality,
                                        double userLat, double userLon,
                                        String fullName, String phoneNumber, String uid) {
        new AlertDialog.Builder(context)
                .setTitle("Send Emergency Request")
                .setMessage("Do you want to notify the rescue station of your emergency?")
                .setPositiveButton("Yes", (dialog, which) ->
                        sendEmergencyToFirestore(adminId, municipality, userLat, userLon,
                                fullName, phoneNumber, uid))
                .setNegativeButton("Cancel", (dialog, which) ->
                        Toast.makeText(context, "Emergency request cancelled", Toast.LENGTH_SHORT).show())
                .show();
    }

    private void sendEmergencyToFirestore(String adminId, String municipality,
                                          double userLat, double userLon,
                                          String fullName, String phoneNumber, String uid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.dialog_loading_with_close);
        builder.setCancelable(false);
        final AlertDialog loadingDialog = builder.create();
        loadingDialog.show();
        Button btnClose = loadingDialog.findViewById(R.id.btnCloseLoading);

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude",      userLat);
        locationData.put("longitude",     userLon);
        locationData.put("municipality",  municipality);
        locationData.put("timestamp",     System.currentTimeMillis());
        locationData.put("reportedBy",    fullName);
        locationData.put("reportedByUid", uid);
        locationData.put("phoneNumber",   phoneNumber);
        locationData.put("status",        "pending");
        locationData.put("adminId",       adminId);
        locationData.put("hasImage",      false);

        db.collection("emergency_locations")
                .add(locationData)
                .addOnSuccessListener(documentReference -> {
                    currentEmergencyDocId = documentReference.getId();
                    final ListenerRegistration[] listener = new ListenerRegistration[1];

                    if (btnClose != null) {
                        btnClose.setOnClickListener(v -> {
                            loadingDialog.dismiss();
                            documentReference.delete();
                            Toast.makeText(context, "Emergency request cancelled", Toast.LENGTH_SHORT).show();
                            if (listener[0] != null) listener[0].remove();
                        });
                    }

                    listener[0] = documentReference.addSnapshotListener((snapshot, e) -> {
                        if (e != null) {
                            loadingDialog.dismiss();
                            Toast.makeText(context, "Error listening for confirmation", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {
                            String status = snapshot.getString("status");
                            if (status != null) {
                                switch (status) {
                                    case "confirmed":
                                        loadingDialog.dismiss();
                                        showConfirmationWithImageOption(documentReference.getId());
                                        if (listener[0] != null) listener[0].remove();
                                        break;
                                    case "rejected":
                                        loadingDialog.dismiss();
                                        showConfirmationDialog("Rescue station could not process your request.");
                                        if (listener[0] != null) listener[0].remove();
                                        break;
                                }
                            }
                        }
                    });

                    Handler handler = new Handler();
                    Runnable timeoutRunnable = () -> {
                        if (loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                            showConfirmationDialog("No response from rescue station. Please try again or call directly.");
                            if (listener[0] != null) listener[0].remove();
                        }
                    };
                    handler.postDelayed(timeoutRunnable, 5 * 60 * 1000);
                    loadingDialog.setOnDismissListener(d -> {
                        handler.removeCallbacks(timeoutRunnable);
                        if (listener[0] != null) listener[0].remove();
                    });
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(context, "Failed to notify station: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showConfirmationDialog(String message) {
        new AlertDialog.Builder(context)
                .setTitle("Rescue Station Response")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showConfirmationWithImageOption(String emergencyDocId) {
        new AlertDialog.Builder(context)
                .setTitle("Emergency Confirmed!")
                .setMessage("Rescue station has confirmed your request!\n\nWould you like to provide a " +
                        "photo of the emergency area to help rescuers better understand the situation?")
                .setPositiveButton("Take Photo", (dialog, which) -> {
                    currentEmergencyDocId = emergencyDocId;
                    checkCameraPermissionAndCapture();
                })
                .setNegativeButton("Skip Photo", (dialog, which) ->
                        Toast.makeText(context, "Help is on the way!", Toast.LENGTH_LONG).show())
                .setNeutralButton("Later", (dialog, which) ->
                        Toast.makeText(context, "You can add a photo later. Help is on the way!",
                                Toast.LENGTH_LONG).show())
                .setCancelable(false)
                .show();
    }

    // ─── Camera / image ───────────────────────────────────────────────────────

    private void checkCameraPermissionAndCapture() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startImageCapture();
        }
    }

    private void startImageCapture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            ((Activity) context).startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(context, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    public void handleImageCaptureResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            if (imageBitmap != null && currentEmergencyDocId != null) {
                uploadImageToFirebase(imageBitmap, currentEmergencyDocId);
            }
        }
    }

    private void uploadImageToFirebase(Bitmap bitmap, String emergencyDocId) {
        AlertDialog uploadDialog = new AlertDialog.Builder(context)
                .setMessage("Processing image...")
                .setCancelable(false)
                .create();
        uploadDialog.show();

        new Thread(() -> {
            try {
                Bitmap compressed = compressBitmap(bitmap);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                compressed.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                byte[] imageData = baos.toByteArray();

                if (imageData.length > 800 * 1024) {
                    ((Activity) context).runOnUiThread(() -> {
                        uploadDialog.dismiss();
                        Toast.makeText(context, "Image too large. Please try another photo.",
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                String base64Image = Base64.encodeToString(imageData, Base64.DEFAULT);

                // ✅ FIXED: Keep Firestore call on background thread
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("imageBase64",    base64Image);
                updateData.put("hasImage",        true);
                updateData.put("imageTimestamp",  System.currentTimeMillis());
                updateData.put("imageSize",       imageData.length);

                db.collection("emergency_locations").document(emergencyDocId)
                        .update(updateData)
                        .addOnSuccessListener(aVoid -> {
                            ((Activity) context).runOnUiThread(() -> {
                                uploadDialog.dismiss();
                                Toast.makeText(context,
                                        "Image saved! This will help rescuers locate you better.",
                                        Toast.LENGTH_LONG).show();
                            });
                        })
                        .addOnFailureListener(e -> {
                            ((Activity) context).runOnUiThread(() -> {
                                uploadDialog.dismiss();
                                Toast.makeText(context, "Failed to save image: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                        });
            } catch (Exception e) {
                ((Activity) context).runOnUiThread(() -> {
                    uploadDialog.dismiss();
                    Toast.makeText(context, "Error processing image: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private Bitmap compressBitmap(Bitmap original) {
        int maxWidth = 800, maxHeight = 600;
        float ratio = Math.min((float) maxWidth / original.getWidth(),
                (float) maxHeight / original.getHeight());
        if (ratio < 1.0f) {
            return Bitmap.createScaledBitmap(original,
                    Math.round(original.getWidth() * ratio),
                    Math.round(original.getHeight() * ratio), true);
        }
        return original;
    }

    // ─── Permission result ────────────────────────────────────────────────────

    public void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startImageCapture();
            } else {
                Toast.makeText(context, "Camera permission is needed to take photos",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "You can now make emergency calls. Please try again.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Phone call permission denied. You can manually dial the number.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}   