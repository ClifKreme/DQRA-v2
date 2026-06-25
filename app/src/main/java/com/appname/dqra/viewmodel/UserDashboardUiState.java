package com.appname.dqra.viewmodel;

import com.appname.dqra.model.RescueStation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserDashboardUiState {

    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        EMPTY,
        ERROR
    }

    private final Status status;
    private final List<RescueStation> stations;
    private final String message;

    private UserDashboardUiState(Status status, List<RescueStation> stations, String message) {
        this.status = status;
        this.stations = stations == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(stations));
        this.message = message;
    }

    public static UserDashboardUiState idle() {
        return new UserDashboardUiState(Status.IDLE, Collections.emptyList(), null);
    }

    public static UserDashboardUiState loading(String message) {
        return new UserDashboardUiState(Status.LOADING, Collections.emptyList(), message);
    }

    public static UserDashboardUiState success(List<RescueStation> stations, String message) {
        return new UserDashboardUiState(Status.SUCCESS, stations, message);
    }

    public static UserDashboardUiState empty(String message) {
        return new UserDashboardUiState(Status.EMPTY, Collections.emptyList(), message);
    }

    public static UserDashboardUiState error(String message) {
        return new UserDashboardUiState(Status.ERROR, Collections.emptyList(), message);
    }

    public Status getStatus() {
        return status;
    }

    public List<RescueStation> getStations() {
        return stations;
    }

    public String getMessage() {
        return message;
    }
}
