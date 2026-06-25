package com.appname.dqra.viewmodel;

public class AdminDashboardUiState {

    public enum Status {
        IDLE,
        LOADING,
        READY,
        ERROR
    }

    private final Status status;
    private final String municipality;
    private final String message;

    private AdminDashboardUiState(Status status, String municipality, String message) {
        this.status = status;
        this.municipality = municipality;
        this.message = message;
    }

    public static AdminDashboardUiState idle() {
        return new AdminDashboardUiState(Status.IDLE, null, null);
    }

    public static AdminDashboardUiState loading() {
        return new AdminDashboardUiState(Status.LOADING, null, null);
    }

    public static AdminDashboardUiState ready(String municipality) {
        return new AdminDashboardUiState(Status.READY, municipality, null);
    }

    public static AdminDashboardUiState error(String message) {
        return new AdminDashboardUiState(Status.ERROR, null, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getMessage() {
        return message;
    }
}
