package com.appname.dqra.viewmodel;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.repository.AdminDashboardRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminDashboardViewModel extends ViewModel {

    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_RESOLVED = "resolved";

    private final AdminDashboardRepository repository;
    private final MutableLiveData<AdminDashboardUiState> dashboardState =
            new MutableLiveData<>(AdminDashboardUiState.idle());
    private final MutableLiveData<AdminEmergencyStreamState> emergencyState =
            new MutableLiveData<>(AdminEmergencyStreamState.empty());
    private final MutableLiveData<AdminEmergencyStats> statsState =
            new MutableLiveData<>(new AdminEmergencyStats(0, 0, 0));
    private final MutableLiveData<String> actionError = new MutableLiveData<>();
    private final MutableLiveData<String> actionSuccess = new MutableLiveData<>();

    private ListenerRegistration emergencyListener;
    private String adminMunicipality = "";
    private String currentEmergencyId = "";

    public AdminDashboardViewModel() {
        this(new AdminDashboardRepository());
    }

    AdminDashboardViewModel(AdminDashboardRepository repository) {
        this.repository = repository;
    }

    public LiveData<AdminDashboardUiState> getDashboardState() {
        return dashboardState;
    }

    public LiveData<AdminEmergencyStreamState> getEmergencyState() {
        return emergencyState;
    }

    public LiveData<AdminEmergencyStats> getStatsState() {
        return statsState;
    }

    public LiveData<String> getActionError() {
        return actionError;
    }

    public LiveData<String> getActionSuccess() {
        return actionSuccess;
    }

    public boolean hasCurrentUser() {
        return repository.hasCurrentUser();
    }

    public String getCurrentUserId() {
        return repository.getCurrentUserId();
    }

    public void signOut() {
        repository.signOut();
    }

    public void setCurrentEmergencyId(String currentEmergencyId) {
        this.currentEmergencyId = currentEmergencyId == null ? "" : currentEmergencyId;
    }

    public void loadAdminMunicipality() {
        dashboardState.setValue(AdminDashboardUiState.loading());
        repository.fetchAdminMunicipality(new AdminDashboardRepository.ValueCallback<String>() {
            @Override
            public void onSuccess(String municipality) {
                adminMunicipality = municipality;
                dashboardState.setValue(AdminDashboardUiState.ready(municipality));
                startEmergencyListener();
                refreshStats();
            }

            @Override
            public void onError(Exception exception) {
                dashboardState.setValue(AdminDashboardUiState.error(exception.getMessage()));
            }
        });
    }

    public void startEmergencyListener() {
        if (adminMunicipality == null || adminMunicipality.isEmpty()) {
            return;
        }

        stopEmergencyListener();
        emergencyListener = repository.listenForEmergencies(
                adminMunicipality,
                currentEmergencyId,
                new AdminDashboardRepository.EmergencyListener() {
                    @Override
                    public void onEmpty() {
                        emergencyState.setValue(AdminEmergencyStreamState.empty());
                    }

                    @Override
                    public void onEmergency(AdminEmergency emergency) {
                        emergencyState.setValue(AdminEmergencyStreamState.emergency(emergency));
                    }

                    @Override
                    public void onError(Exception exception) {
                        emergencyState.setValue(AdminEmergencyStreamState.error(exception.getMessage()));
                    }
                });
    }

    public void stopEmergencyListener() {
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }

    public void refreshStats() {
        if (adminMunicipality == null || adminMunicipality.isEmpty()) {
            statsState.setValue(new AdminEmergencyStats(0, 0, 0));
            return;
        }

        repository.fetchStats(adminMunicipality, new AdminDashboardRepository.ValueCallback<AdminEmergencyStats>() {
            @Override
            public void onSuccess(AdminEmergencyStats value) {
                statsState.setValue(value);
            }

            @Override
            public void onError(Exception exception) {
                actionError.setValue(exception.getMessage());
            }
        });
    }

    public void confirmEmergency(String emergencyId) {
        updateEmergencyStatus(emergencyId, STATUS_CONFIRMED, "Emergency confirmed");
    }

    public void rejectEmergency(String emergencyId) {
        updateEmergencyStatus(emergencyId, STATUS_REJECTED, "Emergency rejected");
    }

    public void resolveEmergency(String emergencyId) {
        updateEmergencyStatus(emergencyId, STATUS_RESOLVED, "Emergency marked as resolved");
    }

    private void updateEmergencyStatus(String emergencyId, String status, String successMessage) {
        repository.updateEmergencyStatus(emergencyId, status, new AdminDashboardRepository.ValueCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                actionSuccess.setValue(successMessage);
                refreshStats();
            }

            @Override
            public void onError(Exception exception) {
                actionError.setValue(exception.getMessage());
            }
        });
    }

    public void updateAdminLocation(Location location) {
        if (location == null || adminMunicipality == null || adminMunicipality.isEmpty()) {
            return;
        }

        repository.updateAdminLocation(location, adminMunicipality,
                new AdminDashboardRepository.ValueCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                    }

                    @Override
                    public void onError(Exception exception) {
                        actionError.setValue(exception.getMessage());
                    }
                });
    }

    public void fetchEmergencyImageData(String emergencyId,
                                        AdminDashboardRepository.ValueCallback<AdminEmergency> callback) {
        repository.fetchEmergencyImageData(emergencyId, callback);
    }

    public ListenerRegistration listenForEmergencyImageData(
            String emergencyId,
            AdminDashboardRepository.ValueCallback<AdminEmergency> callback) {
        return repository.listenForEmergencyImageData(emergencyId, callback);
    }

    public ListenerRegistration listenForWebsiteNotifications(
            String municipality,
            AdminDashboardRepository.WebsiteNotificationListener listener) {
        return repository.listenForWebsiteNotifications(municipality, listener);
    }

    @Override
    protected void onCleared() {
        stopEmergencyListener();
        super.onCleared();
    }
}
