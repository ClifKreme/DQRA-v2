package com.appname.dqra;

// Import necessary Android and Google Play Services libraries
import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

/**
 * LocationUtils - A helper class to get the user's current location using
 * Google's FusedLocationProviderClient.
 */
public class LocationUtils {

    // Client used to access the fused location provider
    private final FusedLocationProviderClient fusedLocationClient;

    /**
     * Constructor to initialize the fused location client with the provided context.
     * @param context Application context needed to access location services.
     */
    public LocationUtils(Context context) {
        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Fetches the last known location of the device.
     * If successful, calls the listener with latitude and longitude.
     * If the location is null, returns 0 for both coordinates.
     *
     * @param listener Callback interface to return the location result.
     */
    public void getCurrentLocation(OnLocationFetchedListener listener) {
        try {
            // Try to get the last known location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                // If location is available, get coordinates
                                double latitude = location.getLatitude();
                                double longitude = location.getLongitude();
                                listener.onLocationFetched(latitude, longitude); // Return location
                            } else {
                                // If location is null, return (0,0) as default
                                listener.onLocationFetched(0, 0);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("LocationUtils", "Failed to fetch location: " + e.getMessage());
                        listener.onLocationFetched(0, 0);
                    });
        } catch (SecurityException e) {
            // Handle the case where location permission is not granted
            Log.e("LocationUtils", "Permission denied: " + e.getMessage());
            listener.onLocationFetched(0, 0);
        }
    }

    /**
     * Interface to handle location callback.
     */
    public interface OnLocationFetchedListener {
        /**
         * Called when the location is successfully fetched.
         * @param latitude  The latitude of the location.
         * @param longitude The longitude of the location.
         */
        void onLocationFetched(double latitude, double longitude);
    }
}
