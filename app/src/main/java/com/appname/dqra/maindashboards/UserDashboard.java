package com.appname.dqra.maindashboards;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.BaseActivity;
import com.appname.dqra.R;
import com.appname.dqra.model.RescueStation;
import com.appname.dqra.user.UserDashboardUiState;
import com.appname.dqra.user.UserDashboardViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class UserDashboard extends BaseActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "UserDashboard";

    private RecyclerView recyclerViewStations;
    private SearchView searchView;
    private TextView tvAutoFetchHint;
    private FloatingActionButton fabRefreshLocation;

    private UserDashboardViewModel viewModel;
    private UserAdapter adapter;
    private List<RescueStation> rescueStationList;

    private double userLat;
    private double userLon;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_user);

        viewModel = new ViewModelProvider(this).get(UserDashboardViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setActiveTab(Tab.HOME);

        initViews();
        observeViewModel();
        setupSearch();
        setupRefreshButton();
        requestLocationPermissionsForAutoFetch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasLocationPermission()) {
            getCurrentLocationForAutoFetch();
        }
        if (adapter != null) {
            adapter.startAutoRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.stopAutoRefresh();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.stopAutoRefresh();
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void initViews() {
        searchView = findViewById(R.id.searchView);
        recyclerViewStations = findViewById(R.id.recyclerViewStations);
        tvAutoFetchHint = findViewById(R.id.tvAutoFetchHint);
        fabRefreshLocation = findViewById(R.id.fabRefreshLocation);

        recyclerViewStations.setLayoutManager(new LinearLayoutManager(this));

        rescueStationList = new ArrayList<>();
        adapter = new UserAdapter(this, rescueStationList);
        recyclerViewStations.setAdapter(adapter);
        recyclerViewStations.setVisibility(View.GONE);

        tvAutoFetchHint.setVisibility(View.GONE);
    }

    private void observeViewModel() {
        viewModel.getStationState().observe(this, this::renderStationState);
    }

    private void renderStationState(UserDashboardUiState state) {
        if (state == null) {
            return;
        }

        String message = state.getMessage();

        switch (state.getStatus()) {
            case LOADING:
                tvAutoFetchHint.setText(message == null ? "Loading rescue stations..." : message);
                tvAutoFetchHint.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                rescueStationList.clear();
                rescueStationList.addAll(state.getStations());
                adapter.setUserLocation(userLat, userLon);
                adapter.notifyDataSetChanged();
                recyclerViewStations.setVisibility(View.VISIBLE);

                if (message != null && !message.isEmpty()) {
                    tvAutoFetchHint.setText(message);
                    tvAutoFetchHint.setVisibility(View.VISIBLE);
                } else {
                    tvAutoFetchHint.setVisibility(View.GONE);
                }
                break;
            case EMPTY:
                rescueStationList.clear();
                adapter.notifyDataSetChanged();
                recyclerViewStations.setVisibility(View.GONE);
                tvAutoFetchHint.setText(message == null ? "No rescue stations available" : message);
                tvAutoFetchHint.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                Toast.makeText(this, "Error loading rescue stations", Toast.LENGTH_SHORT).show();
                recyclerViewStations.setVisibility(View.GONE);
                tvAutoFetchHint.setText(message == null ? "Error loading stations. Please try again." : message);
                tvAutoFetchHint.setVisibility(View.VISIBLE);
                break;
            case IDLE:
            default:
                break;
        }
    }

    private void setupSearch() {
        int searchPlateId = searchView.getContext().getResources()
                .getIdentifier("android:id/search_src_text", null, null);
        android.widget.TextView searchText = searchView.findViewById(searchPlateId);
        if (searchText != null) {
            searchText.setTextColor(android.graphics.Color.BLACK);
            searchText.setHintTextColor(android.graphics.Color.GRAY);
            searchText.setTextSize(16);
        }

        int searchIconId = searchView.getContext().getResources()
                .getIdentifier("android:id/search_mag_icon", null, null);
        android.widget.ImageView searchIcon = searchView.findViewById(searchIconId);
        if (searchIcon != null) searchIcon.setColorFilter(android.graphics.Color.GRAY);

        int closeIconId = searchView.getContext().getResources()
                .getIdentifier("android:id/search_close_btn", null, null);
        android.widget.ImageView closeIcon = searchView.findViewById(closeIconId);
        if (closeIcon != null) closeIcon.setColorFilter(android.graphics.Color.GRAY);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                tvAutoFetchHint.setVisibility(View.GONE);
                recyclerViewStations.setVisibility(View.VISIBLE);
                viewModel.searchStations(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.trim().isEmpty() && hasLocationPermission()) {
                    tvAutoFetchHint.setVisibility(View.VISIBLE);
                    getCurrentLocationForAutoFetch();
                }
                return false;
            }
        });

        searchView.setQueryHint("Search by municipality name...");
    }

    private void setupRefreshButton() {
        fabRefreshLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationForAutoFetch();
                Toast.makeText(this, "Refreshing location...", Toast.LENGTH_SHORT).show();
            } else {
                requestLocationPermissionsForAutoFetch();
            }
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionsForAutoFetch() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocationForAutoFetch();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void getCurrentLocationForAutoFetch() {
        if (!hasLocationPermission()) {
            tvAutoFetchHint.setText("Location permission needed to find nearest station");
            tvAutoFetchHint.setVisibility(View.VISIBLE);
            return;
        }

        tvAutoFetchHint.setText("Finding nearest rescue station...");
        tvAutoFetchHint.setVisibility(View.VISIBLE);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                updateUserLocation(location);
                viewModel.findClosestStation();
            } else {
                requestFreshLocationForAutoFetch();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get last location: " + e.getMessage());
            requestFreshLocationForAutoFetch();
        });
    }

    @SuppressWarnings("MissingPermission")
    private void requestFreshLocationForAutoFetch() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(true)
                .setMaxUpdates(1)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && !locationResult.getLocations().isEmpty()) {
                    Location loc = locationResult.getLastLocation();
                    updateUserLocation(loc);
                    viewModel.findClosestStation();
                    showAutoFetchMessage();
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                } else {
                    tvAutoFetchHint.setText("Unable to get location. Search manually instead.");
                    Toast.makeText(UserDashboard.this,
                            "Search for your municipality to find rescue stations",
                            Toast.LENGTH_LONG).show();
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateUserLocation(Location location) {
        userLat = location.getLatitude();
        userLon = location.getLongitude();
        viewModel.setUserLocation(userLat, userLon);
        adapter.setUserLocation(userLat, userLon);
    }

    private void showAutoFetchMessage() {
        Toast.makeText(this, "Showing nearest rescue station to your location", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (adapter != null) {
            adapter.handlePermissionResult(requestCode, permissions, grantResults);
        }

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean locationGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if ((permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION))
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    locationGranted = true;
                }
            }

            if (locationGranted) {
                getCurrentLocationForAutoFetch();
            } else {
                tvAutoFetchHint.setText("Search for your municipality to find rescue stations");
                tvAutoFetchHint.setVisibility(View.VISIBLE);
                Toast.makeText(this,
                        "Location permission needed for automatic station detection",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (adapter != null) {
            adapter.handleImageCaptureResult(requestCode, resultCode, data);
        }
    }
}
