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
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.R;
import com.appname.dqra.image.Base64ImageHelper;
import com.appname.dqra.model.RescueStation;
import com.appname.dqra.model.UserProfile;
import com.appname.dqra.repository.AccountRepository;
import com.appname.dqra.repository.EmergencyRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    private Context context;
    private List<RescueStation> rescueStationList;
    private final AccountRepository accountRepository;
    private final EmergencyRepository emergencyRepository;
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

    // Spam prevention — cooldown between emergency submissions
    private static final long CANCEL_COOLDOWN_BASE_MS = 2 * 60 * 1000L;
    private static final long CANCEL_COOLDOWN_MAX_MS = 30 * 60 * 1000L;
    private static final long RESOLVED_COOLDOWN_BASE_MS = 3 * 60 * 1000L;
    private static final long RESOLVED_COOLDOWN_MAX_MS = 60 * 60 * 1000L;
    private long cancelCooldownUntil = 0L;
    private int consecutiveCancels = 0;
    private boolean emergencyInFlight = false;

    // Guards against multiple dialogs/toasts when buttons are tapped while network is slow
    private boolean emergencyDialogShowing = false;
    private boolean messengerInFlight = false;

    public UserAdapter(Context context, List<RescueStation> rescueStationList) {
        this.context = context;
        this.rescueStationList = rescueStationList;
        this.accountRepository = new AccountRepository();
        this.emergencyRepository = new EmergencyRepository();
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
        bindStationProfileImage(holder.imgStationProfile, station.getProfileImageBase64());

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
        holder.tvPhoneNumber.setPaintFlags(holder.tvPhoneNumber.getPaintFlags()
                | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        holder.tvPhoneNumber.setClickable(true);
        holder.tvPhoneNumber.setOnClickListener(v -> showCallOptionsDialog(station));

        // ── Messenger button ───────────────────────────────────────────────────
        holder.btnMessenger.setOnClickListener(v -> {
            if (messengerInFlight) return;
            messengerInFlight = true;
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
            holder.progressAction.setVisibility(View.GONE);
            holder.btnAction.setEnabled(true);
            holder.btnAction.setOnClickListener(v -> {
                // Show spinner on the button, hide text, disable to prevent double-tap
                holder.btnAction.setText("");
                holder.btnAction.setEnabled(false);
                holder.progressAction.setVisibility(View.VISIBLE);
                checkLocationPermissions(station.getId(), station.getMunicipality(),
                        holder.btnAction, holder.progressAction);
            });
        } else {
            holder.btnAction.setText("📞 CALL RESCUE STATION");
            holder.btnAction.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF2E7D32));
            holder.progressAction.setVisibility(View.GONE);
            holder.btnAction.setEnabled(true);
            holder.btnAction.setOnClickListener(v ->
                    showCallOptionsDialog(station));
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
        public ImageView imgStationProfile;
        public Button   btnAction;
        public Button   btnMessenger;
        public android.widget.ProgressBar progressAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgStationProfile = itemView.findViewById(R.id.imgStationProfile);
            tvStationName  = itemView.findViewById(R.id.stationName);
            tvMunicipality = itemView.findViewById(R.id.tvMunicipality);
            tvPhoneNumber  = itemView.findViewById(R.id.stationPhone);
            btnAction      = itemView.findViewById(R.id.btnAction);
            btnMessenger   = itemView.findViewById(R.id.btnMessenger);
            progressAction = itemView.findViewById(R.id.progressAction);
        }
    }

    // ─── Distance helper ──────────────────────────────────────────────────────

    private void bindStationProfileImage(ImageView imageView, String profileImageBase64) {
        if (imageView == null) {
            return;
        }

        imageView.setClipToOutline(true);
        imageView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });

        if (profileImageBase64 != null
                && !profileImageBase64.trim().isEmpty()
                && Base64ImageHelper.loadBase64IntoImageView(imageView, profileImageBase64)) {
            return;
        }

        imageView.setImageResource(R.drawable.ic_profile_placeholder);
    }

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

        if ((phone1 == null || phone1.trim().isEmpty())
                && (phone2 == null || phone2.trim().isEmpty())) {
            Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show();
            return;
        }

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
                    messengerInFlight = false;
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
                .setNegativeButton("Cancel", (dialog, which) -> {
                    messengerInFlight = false;
                    Toast.makeText(context, "Location sharing requires internet connection",
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Try Anyway", (dialog, which) -> {
                    Toast.makeText(context,
                            "⚠️ No internet detected. Please enable Mobile Data before sending.",
                            Toast.LENGTH_LONG).show();
                    getLocationAndShareMessage(station);
                })
                .setOnCancelListener(dialog -> messengerInFlight = false)
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
        if (!accountRepository.hasCurrentUser()) {
            showShareSheet(createLocationMessage(null, null, stationName, lat, lon),
                    stationName, stationLocation);
            return;
        }

        // Use a flag so the callback only fires once.
        // loadCurrentUserProfile calls onSuccess twice when cache + Firestore both respond.
        final boolean[] shown = {false};
        accountRepository.loadCurrentUserProfile(new AccountRepository.Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (shown[0]) return;
                shown[0] = true;
                showShareSheet(createLocationMessage(profile.getFullName(), profile.getPhone(),
                                stationName, lat, lon),
                        stationName, stationLocation);
            }

            @Override
            public void onError(Exception exception) {
                if (shown[0]) return;
                shown[0] = true;
                showShareSheet(createLocationMessage(null, null, stationName, lat, lon),
                        stationName, stationLocation);
            }
        });
    }

    private void showShareSheet(String message, String stationName, String stationLocation) {
        try {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Emergency Message", message));
            showMessengerInstructions(stationName, stationLocation, message);
        } catch (Exception e) {
            messengerInFlight = false;
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
                    messengerInFlight = false;
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "🚨 EMERGENCY - " + stationName);
                    context.startActivity(Intent.createChooser(shareIntent, "Select Messenger or messaging app"));
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    messengerInFlight = false;
                    Toast.makeText(context, "Message cancelled", Toast.LENGTH_SHORT).show();
                })
                .setOnCancelListener(dialog -> messengerInFlight = false)
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

    /** Restores the emergency button to its normal state after loading completes. */
    private void resetActionButton(Button btn, android.widget.ProgressBar progress) {
        if (btn != null) {
            btn.setText("🚨 SEND EMERGENCY ALERT");
            btn.setEnabled(true);
        }
        if (progress != null) {
            progress.setVisibility(View.GONE);
        }
    }

    private void checkLocationPermissions(String adminId, String municipality,
                                          Button actionBtn, android.widget.ProgressBar actionProgress) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            resetActionButton(actionBtn, actionProgress);
            ActivityCompat.requestPermissions((Activity) context,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fetchAllAndPrepareAlert(adminId, municipality, actionBtn, actionProgress);
        }
    }

    /**
     * Fires GPS lookup and profile fetch IN PARALLEL, then runs both cooldown checks
     * IN PARALLEL once we have the uid. Only then shows the confirmation dialog.
     * This replaces the old sequential chain of 4 async calls.
     */
    private void fetchAllAndPrepareAlert(String adminId, String municipality,
                                         Button actionBtn, android.widget.ProgressBar actionProgress) {
        if (!accountRepository.hasCurrentUser()) {
            resetActionButton(actionBtn, actionProgress);
            Toast.makeText(context, "You must be logged in to send a request.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (emergencyInFlight) {
            resetActionButton(actionBtn, actionProgress);
            Toast.makeText(context, "Your emergency request is already being sent. Please wait.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent multiple dialogs/toasts if the button is tapped again while async calls are in progress
        if (emergencyDialogShowing) {
            resetActionButton(actionBtn, actionProgress);
            return;
        }
        emergencyDialogShowing = true;

        long now = System.currentTimeMillis();
        if (cancelCooldownUntil > now) {
            resetActionButton(actionBtn, actionProgress);
            emergencyDialogShowing = false;
            long secondsLeft = (cancelCooldownUntil - now + 999) / 1000;
            new AlertDialog.Builder(context)
                    .setTitle("⏳ Please Wait")
                    .setMessage("You recently cancelled an emergency request.\n\n" +
                            "You can send another in " + secondsLeft + " second" +
                            (secondsLeft != 1 ? "s" : "") + ".\n\n" +
                            "If this is a different emergency, please call the station directly.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("📞 Call Station", (d, w) -> {
                        for (RescueStation s : rescueStationList) {
                            if (adminId.equals(s.getId())) {
                                showCallOptionsDialog(s);
                                return;
                            }
                        }
                    })
                    .setOnDismissListener(d -> emergencyDialogShowing = false)
                    .show();
            return;
        }

        // ── Parallel phase 1: GPS + cached profile at the same time ──────────
        final double[] locationResult   = {0, 0};   // [lat, lon]
        final UserProfile[] profileResult = {null};
        final boolean[] locationDone    = {false};
        final boolean[] profileDone     = {false};
        final boolean[] failed          = {false};

        Handler mainHandler = new Handler(Looper.getMainLooper());

        Runnable onBothReady = () -> {
            if (!locationDone[0] || !profileDone[0]) return;
            if (failed[0]) return;

            UserProfile profile = profileResult[0];
            if (profile == null) {
                resetActionButton(actionBtn, actionProgress);
                emergencyDialogShowing = false;
                Toast.makeText(context, "Failed to load profile. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            double lat          = locationResult[0];
            double lon          = locationResult[1];
            String uid          = profile.getId();
            String fullName     = profile.getFullName();
            String phone        = profile.getPhone();
            String profileImage = profile.getProfileImageBase64();

            // ── Parallel phase 2: active-emergency check + resolved cooldown ──
            final Boolean[] hasActive    = {null};
            final EmergencyRepository.CooldownState[] cooldown = {null};
            final boolean[] activeDone   = {false};
            final boolean[] cooldownDone = {false};
            final boolean[] phase2Failed = {false};

            Runnable onPhase2Ready = () -> {
                if (!activeDone[0] || !cooldownDone[0]) return;
                if (phase2Failed[0]) return;

                if (Boolean.TRUE.equals(hasActive[0])) {
                    resetActionButton(actionBtn, actionProgress);
                    emergencyDialogShowing = false;
                    Toast.makeText(context, "You already have an active emergency request!",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                EmergencyRepository.CooldownState cs = cooldown[0];
                if (cs != null && cs.isOnCooldown()) {
                    resetActionButton(actionBtn, actionProgress);
                    emergencyDialogShowing = false;
                    showResolvedCooldownDialog(adminId, cs);
                    return;
                }

                // All checks passed — restore button and show confirmation
                resetActionButton(actionBtn, actionProgress);
                new AlertDialog.Builder(context)
                        .setTitle("Send Emergency Request")
                        .setMessage("Do you want to notify the rescue station of your emergency?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            emergencyDialogShowing = false;
                            sendEmergencyToFirestore(adminId, municipality, lat, lon,
                                    fullName, phone, uid, profileImage);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            emergencyDialogShowing = false;
                            Toast.makeText(context, "Emergency request cancelled",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setOnCancelListener(dialog -> emergencyDialogShowing = false)
                        .show();
            };

            emergencyRepository.hasActiveEmergencyForUser(uid,
                    new EmergencyRepository.Callback<Boolean>() {
                @Override public void onSuccess(Boolean result) {
                    mainHandler.post(() -> {
                        hasActive[0]  = result;
                        activeDone[0] = true;
                        onPhase2Ready.run();
                    });
                }
                @Override public void onError(Exception e) {
                    mainHandler.post(() -> {
                        if (phase2Failed[0]) return;
                        phase2Failed[0] = true;
                        resetActionButton(actionBtn, actionProgress);
                        emergencyDialogShowing = false;
                        Toast.makeText(context, "Error checking requests: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });

            emergencyRepository.getResolvedEmergencyCooldown(uid,
                    RESOLVED_COOLDOWN_BASE_MS, RESOLVED_COOLDOWN_MAX_MS,
                    new EmergencyRepository.Callback<EmergencyRepository.CooldownState>() {
                @Override public void onSuccess(EmergencyRepository.CooldownState result) {
                    mainHandler.post(() -> {
                        cooldown[0]     = result;
                        cooldownDone[0] = true;
                        onPhase2Ready.run();
                    });
                }
                @Override public void onError(Exception e) {
                    mainHandler.post(() -> {
                        if (phase2Failed[0]) return;
                        phase2Failed[0] = true;
                        resetActionButton(actionBtn, actionProgress);
                        emergencyDialogShowing = false;
                        Toast.makeText(context, "Error checking cooldown: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        };

        // Kick off GPS (uses cached last-known — fastest possible)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            locationResult[0] = location.getLatitude();
                            locationResult[1] = location.getLongitude();
                        }
                        locationDone[0] = true;
                        onBothReady.run();
                    })
                    .addOnFailureListener(e -> {
                        locationDone[0] = true;
                        onBothReady.run();
                    });
        } else {
            locationDone[0] = true;
        }

        // Kick off profile fetch simultaneously — cache-first, one-shot guard
        final boolean[] profileCallbackFired = {false};
        accountRepository.loadCurrentUserProfile(new AccountRepository.Callback<UserProfile>() {
            @Override public void onSuccess(UserProfile profile) {
                if (profileCallbackFired[0]) return;
                profileCallbackFired[0] = true;
                profileResult[0] = profile;
                profileDone[0]   = true;
                onBothReady.run();
            }
            @Override public void onError(Exception e) {
                if (profileCallbackFired[0]) return;
                profileCallbackFired[0] = true;
                if (failed[0]) return;
                failed[0] = true;
                resetActionButton(actionBtn, actionProgress);
                emergencyDialogShowing = false;
                Toast.makeText(context, "Failed to fetch user data: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showResolvedCooldownDialog(String adminId,
                                            EmergencyRepository.CooldownState cooldownState) {
        long minutesLeft = getMinutesFromMs(cooldownState.getRemainingMs());
        long cooldownMinutes = getMinutesFromMs(cooldownState.getCooldownMs());
        new AlertDialog.Builder(context)
                .setTitle("Please Wait")
                .setMessage("Your last emergency was already resolved.\n\n" +
                        "For safety and spam prevention, you need to wait " + minutesLeft +
                        " minute" + (minutesLeft != 1 ? "s" : "") +
                        " before sending another emergency.\n\n" +
                        "Current cooldown: " + cooldownMinutes + " minute" +
                        (cooldownMinutes != 1 ? "s" : "") + ".\n" +
                        "The limit can increase up to 1 hour after repeated emergencies.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Call Station", (d, w) -> {
                    for (RescueStation station : rescueStationList) {
                        if (adminId.equals(station.getId())) {
                            showCallOptionsDialog(station);
                            return;
                        }
                    }
                })
                .show();
    }

    private void sendEmergencyToFirestore(String adminId, String municipality,
                                          double userLat, double userLon,
                                          String fullName, String phoneNumber, String uid,
                                          String profileImageBase64) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.dialog_loading_with_close);
        builder.setCancelable(false);
        final AlertDialog loadingDialog = builder.create();
        loadingDialog.show();
        Button btnClose = loadingDialog.findViewById(R.id.btnCloseLoading);

        emergencyInFlight = true;

        // Safety net: if the Firestore create call never responds (network drop, etc.),
        // reset the flag after 30 seconds so the button works again.
        Handler safetyHandler = new Handler(Looper.getMainLooper());
        Runnable safetyReset = () -> {
            if (emergencyInFlight) {
                emergencyInFlight = false;
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }
                Toast.makeText(context,
                        "Request timed out. Please check your connection and try again.",
                        Toast.LENGTH_LONG).show();
            }
        };
        safetyHandler.postDelayed(safetyReset, 30_000);

        // Also reset the flag whenever the dialog is dismissed for any reason
        // (covers rotation, back-press bypasses since cancelable=false, OS kill, etc.)
        loadingDialog.setOnDismissListener(d -> {
            emergencyInFlight = false;
            safetyHandler.removeCallbacks(safetyReset);
        });

        emergencyRepository.createEmergencyRequest(adminId, municipality, userLat, userLon,
                fullName, phoneNumber, uid, profileImageBase64, new EmergencyRepository.Callback<String>() {
                @Override
                public void onSuccess(String emergencyDocId) {
                    safetyHandler.removeCallbacks(safetyReset);
                    emergencyInFlight = false;
                    currentEmergencyDocId = emergencyDocId;
                    final EmergencyRepository.ListenerHandle[] listener = new EmergencyRepository.ListenerHandle[1];

                    if (btnClose != null) {
                        btnClose.setOnClickListener(v -> {
                            showCancelEmergencyConfirmation(loadingDialog, emergencyDocId, listener[0]);
                        });
                    }

                    listener[0] = emergencyRepository.listenToEmergencyStatus(emergencyDocId,
                            new EmergencyRepository.EmergencyStatusListener() {
                        @Override
                        public void onStatusChanged(String status) {
                            if (status != null) {
                                switch (status) {
                                    case "confirmed":
                                        loadingDialog.dismiss();
                                        showConfirmationWithImageOption(emergencyDocId);
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

                        @Override
                        public void onDeleted() {
                            loadingDialog.dismiss();
                            if (listener[0] != null) listener[0].remove();
                        }

                        @Override
                        public void onError(Exception exception) {
                            loadingDialog.dismiss();
                            Toast.makeText(context, "Error listening for confirmation", Toast.LENGTH_SHORT).show();
                        }
                    });

                    Handler timeoutHandler = new Handler(Looper.getMainLooper());
                    Runnable timeoutRunnable = () -> {
                        if (loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                            showConfirmationDialog("No response from rescue station. Please try again or call directly.");
                            if (listener[0] != null) listener[0].remove();
                        }
                    };
                    timeoutHandler.postDelayed(timeoutRunnable, 5 * 60 * 1000);

                    // Override dismiss listener now that we have the status listener to clean up too
                    loadingDialog.setOnDismissListener(d -> {
                        emergencyInFlight = false;
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        if (listener[0] != null) listener[0].remove();
                    });
                }

                @Override
                public void onError(Exception exception) {
                    safetyHandler.removeCallbacks(safetyReset);
                    emergencyInFlight = false;
                    loadingDialog.dismiss();
                    Toast.makeText(context, "Failed to notify station: " + exception.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
        });
    }

    private void showConfirmationDialog(String message) {
        new AlertDialog.Builder(context)
                .setTitle("Rescue Station Response")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showCancelEmergencyConfirmation(AlertDialog loadingDialog,
                                                 String emergencyDocId,
                                                 EmergencyRepository.ListenerHandle listener) {
        new AlertDialog.Builder(context)
                .setTitle("Cancel emergency request?")
                .setMessage("Are you sure you want to cancel this emergency request?\n\n" +
                        "If you cancel, you must wait before sending another emergency. " +
                        "Repeated cancellations will make the wait longer.")
                .setPositiveButton("Yes, Cancel", (dialog, which) ->
                        cancelEmergencyWithCooldown(loadingDialog, emergencyDocId, listener))
                .setNegativeButton("Keep Waiting", null)
                .show();
    }

    private void cancelEmergencyWithCooldown(AlertDialog loadingDialog,
                                             String emergencyDocId,
                                             EmergencyRepository.ListenerHandle listener) {
        emergencyRepository.cancelEmergency(emergencyDocId, new EmergencyRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (listener != null) {
                    listener.remove();
                }
                loadingDialog.dismiss();
                startCancelCooldown();
                Toast.makeText(context,
                        "Emergency request cancelled. Please wait " + getCooldownMinutes()
                                + " minute(s) before sending another.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(context, "Failed to cancel request: " + exception.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startCancelCooldown() {
        consecutiveCancels++;
        long cooldown = CANCEL_COOLDOWN_BASE_MS * (1L << Math.min(consecutiveCancels - 1, 4));
        cooldown = Math.min(cooldown, CANCEL_COOLDOWN_MAX_MS);
        cancelCooldownUntil = System.currentTimeMillis() + cooldown;
    }

    private long getCooldownMinutes() {
        long remaining = Math.max(0, cancelCooldownUntil - System.currentTimeMillis());
        return Math.max(1, (remaining + 59_999L) / 60_000L);
    }

    private long getMinutesFromMs(long durationMs) {
        return Math.max(1, (Math.max(0, durationMs) + 59_999L) / 60_000L);
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
                uploadEmergencyImage(imageBitmap, currentEmergencyDocId);
            }
        }
    }

    private void uploadEmergencyImage(Bitmap bitmap, String emergencyDocId) {
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

                emergencyRepository.updateEmergencyImage(emergencyDocId, base64Image, imageData.length,
                        new EmergencyRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            ((Activity) context).runOnUiThread(() -> {
                                uploadDialog.dismiss();
                                Toast.makeText(context,
                                        "Image saved! This will help rescuers locate you better.",
                                        Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onError(Exception exception) {
                            ((Activity) context).runOnUiThread(() -> {
                                uploadDialog.dismiss();
                                Toast.makeText(context, "Failed to save image: " + exception.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
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
