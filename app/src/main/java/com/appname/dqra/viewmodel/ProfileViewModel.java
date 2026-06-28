package com.appname.dqra.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.model.UserProfile;
import com.appname.dqra.repository.AccountRepository;

import java.util.concurrent.TimeUnit;

public class ProfileViewModel extends ViewModel {

    private static final long UPDATE_COOLDOWN_MS = TimeUnit.DAYS.toMillis(30);

    private final AccountRepository repository;
    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<OperationState> state =
            new MutableLiveData<>(OperationState.idle());

    public ProfileViewModel() {
        this(new AccountRepository());
    }

    ProfileViewModel(AccountRepository repository) {
        this.repository = repository;
    }

    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    public LiveData<OperationState> getState() {
        return state;
    }

    /**
     * Returns how many days remain in the cooldown, or 0 if the user can update now.
     * Returns 0 also when the profile has never been updated before.
     */
    public long getDaysUntilNextUpdate() {
        UserProfile current = profile.getValue();
        if (current == null || current.getProfileLastUpdatedAt() == 0L) return 0;
        long elapsed = System.currentTimeMillis() - current.getProfileLastUpdatedAt();
        long remaining = UPDATE_COOLDOWN_MS - elapsed;
        if (remaining <= 0) return 0;
        return (long) Math.ceil(remaining / (double) TimeUnit.DAYS.toMillis(1));
    }

    public boolean canUpdateProfile() {
        return getDaysUntilNextUpdate() == 0;
    }

    /**
     * Returns how many days remain before the photo can be changed, or 0 if allowed now.
     */
    public long getDaysUntilNextPhotoUpdate() {
        UserProfile current = profile.getValue();
        if (current == null || current.getPhotoLastUpdatedAt() == 0L) return 0;
        long elapsed = System.currentTimeMillis() - current.getPhotoLastUpdatedAt();
        long remaining = UPDATE_COOLDOWN_MS - elapsed;
        if (remaining <= 0) return 0;
        return (long) Math.ceil(remaining / (double) TimeUnit.DAYS.toMillis(1));
    }

    public boolean canUpdatePhoto() {
        return getDaysUntilNextPhotoUpdate() == 0;
    }

    public void loadProfile() {
        state.setValue(OperationState.loading());
        repository.loadCurrentUserProfile(new AccountRepository.Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile value) {
                profile.setValue(value);
                state.setValue(OperationState.idle());
            }

            @Override
            public void onError(Exception exception) {
                state.setValue(OperationState.error("Failed to load profile: " + exception.getMessage()));
            }
        });
    }

    public void saveProfile(String fullName, String phone, String address) {
        if (isOperationInProgress()) {
            state.setValue(OperationState.error("Profile update already in progress."));
            return;
        }

        if (!canUpdateProfile()) {
            long daysLeft = getDaysUntilNextUpdate();
            state.setValue(OperationState.error(
                    "You can only update your profile once per month. "
                    + "Please wait " + daysLeft + " more day(s)."));
            return;
        }

        state.setValue(OperationState.loading());
        repository.updateCurrentUserProfile(fullName, phone, address,
                new AccountRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        UserProfile currentProfile = profile.getValue();
                        profile.setValue(new UserProfile(repository.getCurrentUserId(),
                                fullName,
                                currentProfile == null ? null : currentProfile.getEmail(),
                                phone,
                                address,
                                currentProfile == null ? "User" : currentProfile.getRole(),
                                currentProfile == null ? null : currentProfile.getProfileImageBase64()));
                        state.setValue(OperationState.success("Profile updated"));
                    }

                    @Override
                    public void onError(Exception exception) {
                        state.setValue(OperationState.error("Update failed: " + exception.getMessage()));
                    }
                });
    }

    public void saveProfileImage(String profileImageBase64) {
        if (isOperationInProgress()) {
            state.setValue(OperationState.error("Profile update already in progress."));
            return;
        }

        if (!canUpdatePhoto()) {
            long daysLeft = getDaysUntilNextPhotoUpdate();
            state.setValue(OperationState.error(
                    "You can only change your profile photo once per month. "
                    + "Please wait " + daysLeft + " more day(s)."));
            return;
        }

        state.setValue(OperationState.loading());
        repository.updateCurrentUserProfileImage(profileImageBase64,
                new AccountRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        UserProfile currentProfile = profile.getValue();
                        if (currentProfile != null) {
                            profile.setValue(new UserProfile(
                                    currentProfile.getId(),
                                    currentProfile.getFullName(),
                                    currentProfile.getEmail(),
                                    currentProfile.getPhone(),
                                    currentProfile.getAddress(),
                                    currentProfile.getRole(),
                                    profileImageBase64,
                                    currentProfile.getProfileLastUpdatedAt(),
                                    System.currentTimeMillis()));
                        }
                        state.setValue(OperationState.success("Profile photo updated"));
                    }

                    @Override
                    public void onError(Exception exception) {
                        state.setValue(OperationState.error("Photo update failed: " + exception.getMessage()));
                    }
                });
    }

    public void signOut() {
        repository.signOut();
    }

    private boolean isOperationInProgress() {
        OperationState currentState = state.getValue();
        return currentState != null && currentState.getStatus() == OperationState.Status.LOADING;
    }
}
