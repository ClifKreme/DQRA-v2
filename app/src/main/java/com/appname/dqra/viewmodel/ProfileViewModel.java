package com.appname.dqra.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.model.UserProfile;
import com.appname.dqra.repository.AccountRepository;

public class ProfileViewModel extends ViewModel {

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
                                    profileImageBase64));
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
}
