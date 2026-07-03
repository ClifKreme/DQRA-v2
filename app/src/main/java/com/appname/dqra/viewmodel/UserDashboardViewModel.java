package com.appname.dqra.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.model.RescueStation;
import com.appname.dqra.repository.UserDashboardRepository;

import java.util.List;

public class UserDashboardViewModel extends ViewModel {

    private final UserDashboardRepository repository;
    private final MutableLiveData<UserDashboardUiState> stationState =
            new MutableLiveData<>(UserDashboardUiState.idle());

    private double userLat;
    private double userLon;

    public UserDashboardViewModel() {
        this(new UserDashboardRepository());
    }

    UserDashboardViewModel(UserDashboardRepository repository) {
        this.repository = repository;
    }

    public LiveData<UserDashboardUiState> getStationState() {
        return stationState;
    }

    public void setUserLocation(double userLat, double userLon) {
        this.userLat = userLat;
        this.userLon = userLon;
    }

    public double getUserLat() {
        return userLat;
    }

    public double getUserLon() {
        return userLon;
    }

    public void searchStations(String query) {
        stationState.setValue(UserDashboardUiState.loading("Searching rescue stations..."));
        repository.searchStations(query, new UserDashboardRepository.StationResultCallback() {
            @Override
            public void onSuccess(List<RescueStation> stations) {
                if (stations.isEmpty()) {
                    stationState.setValue(UserDashboardUiState.empty(
                            "No rescue stations found for: " + query));
                    return;
                }
                stationState.setValue(UserDashboardUiState.success(stations, null));
            }

            @Override
            public void onError(Exception exception) {
                stationState.setValue(UserDashboardUiState.error(
                        "Offline mode. Connect to internet once to load rescue stations, or use cached stations if available."));
            }
        });
    }

    public void findClosestStation() {
        stationState.setValue(UserDashboardUiState.loading("Finding nearest rescue station..."));
        repository.findClosestStation(userLat, userLon, new UserDashboardRepository.StationResultCallback() {
            @Override
            public void onSuccess(List<RescueStation> stations) {
                if (stations.isEmpty()) {
                    stationState.setValue(UserDashboardUiState.empty(
                            "No stations with location data found"));
                    return;
                }

                RescueStation closestStation = stations.get(0);
                double distance = UserDashboardRepository.calculateDistance(userLat, userLon,
                        closestStation.getLatitude(), closestStation.getLongitude());
                String distanceText = String.format("%.1f", distance);
                stationState.setValue(UserDashboardUiState.success(stations,
                        "Nearest station: " + closestStation.getMunicipality()
                                + " (" + distanceText + " km away)"));
            }

            @Override
            public void onError(Exception exception) {
                stationState.setValue(UserDashboardUiState.error(
                        "Offline mode. Connect to internet once to load nearest rescue station data."));
            }
        });
    }
}
