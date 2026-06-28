package com.appname.dqra.repository;

import android.util.Log;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.model.RescueStation;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserDashboardRepository {

    public interface StationResultCallback {
        void onSuccess(List<RescueStation> stations);
        void onError(Exception exception);
    }

    private static final String TAG = "UserDashboardRepo";
    private static final String STATIONS_COLLECTION = "admins_with_stations";

    private final FirebaseFirestore firestore;

    public UserDashboardRepository() {
        this(FirebaseFirestore.getInstance());
    }

    UserDashboardRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void searchStations(String query, StationResultCallback callback) {
        String searchQuery = query == null ? "" : query.trim().toLowerCase();

        fetchAllStations(new StationResultCallback() {
            @Override
            public void onSuccess(List<RescueStation> stations) {
                List<RescueStation> matches = new ArrayList<>();
                for (RescueStation station : stations) {
                    String municipality = station.getMunicipality();
                    String stationName = station.getName();

                    if (municipality == null || stationName == null) {
                        continue;
                    }

                    boolean matchesMunicipality = municipality.toLowerCase().contains(searchQuery);
                    boolean matchesName = stationName.toLowerCase().contains(searchQuery);
                    if (matchesMunicipality || matchesName) {
                        matches.add(station);
                    }
                }

                Collections.sort(matches, (s1, s2) -> {
                    String m1 = s1.getMunicipality() == null ? "" : s1.getMunicipality();
                    String m2 = s2.getMunicipality() == null ? "" : s2.getMunicipality();
                    boolean s1Exact = m1.toLowerCase().startsWith(searchQuery);
                    boolean s2Exact = m2.toLowerCase().startsWith(searchQuery);
                    if (s1Exact && !s2Exact) return -1;
                    if (!s1Exact && s2Exact) return 1;
                    return m1.compareToIgnoreCase(m2);
                });

                callback.onSuccess(matches);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void findClosestStation(double userLat, double userLon, StationResultCallback callback) {
        fetchAllStations(new StationResultCallback() {
            @Override
            public void onSuccess(List<RescueStation> stations) {
                RescueStation closestStation = null;
                double minDistance = Double.MAX_VALUE;

                for (RescueStation station : stations) {
                    if (station.getLatitude() == 0 || station.getLongitude() == 0) {
                        continue;
                    }

                    double distance = calculateDistance(userLat, userLon,
                            station.getLatitude(), station.getLongitude());

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestStation = station;
                    }
                }

                if (closestStation == null) {
                    callback.onSuccess(Collections.emptyList());
                    return;
                }

                List<RescueStation> closest = new ArrayList<>();
                closest.add(closestStation);
                callback.onSuccess(closest);
            }

            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }
        });
    }

    private void fetchAllStations(StationResultCallback callback) {
        List<RescueStation> cachedStations = AppCache.getInstance().getRescueStations();
        boolean hasCache = cachedStations != null && !cachedStations.isEmpty();
        if (hasCache) {
            callback.onSuccess(cachedStations);
        }

        firestore.collection(STATIONS_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<RescueStation> stations = new ArrayList<>();
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        stations.add(stationFromDocument(document));
                    }
                    AppCache.getInstance().putRescueStations(stations);
                    callback.onSuccess(stations);
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    private RescueStation stationFromDocument(QueryDocumentSnapshot document) {
        String name = document.getString("stationName");
        String municipality = document.getString("municipality");
        String phoneNumber = document.getString("stationPhone");
        String phoneNumber2 = document.getString("stationPhone2");
        String messengerLink = document.getString("messengerLink");
        String profileImageBase64 = document.getString("profileImageBase64");

        RescueStation station = new RescueStation(name, municipality, phoneNumber);
        station.setStationName(name);
        station.setId(document.getId());
        station.setPhoneNumber2(phoneNumber2);
        station.setMessengerLink(messengerLink);
        station.setProfileImageBase64(profileImageBase64);

        try {
            if (document.contains("latitude") && document.contains("longitude")) {
                Double latitude = document.getDouble("latitude");
                Double longitude = document.getDouble("longitude");
                if (latitude != null) {
                    station.setLatitude(latitude);
                }
                if (longitude != null) {
                    station.setLongitude(longitude);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting location for station: " + e.getMessage());
        }

        return station;
    }

    public static double calculateDistance(double userLat, double userLon,
                                           double stationLat, double stationLon) {
        final int earthRadiusKm = 6371;
        double latDistance = Math.toRadians(stationLat - userLat);
        double lonDistance = Math.toRadians(stationLon - userLon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(stationLat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
