package com.appname.dqra.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.repository.AccountRepository;

public class CompleteProfileViewModel extends ViewModel {

    private final AccountRepository repository;
    private final MutableLiveData<OperationState> state =
            new MutableLiveData<>(OperationState.idle());

    public CompleteProfileViewModel() {
        this(new AccountRepository());
    }

    CompleteProfileViewModel(AccountRepository repository) {
        this.repository = repository;
    }

    public LiveData<OperationState> getState() {
        return state;
    }

    public void completeProfile(String fullName, String phone, String address) {
        state.setValue(OperationState.loading());
        repository.completeCurrentUserProfile(fullName, phone, address,
                new AccountRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        state.setValue(OperationState.success("Profile completed successfully!"));
                    }

                    @Override
                    public void onError(Exception exception) {
                        state.setValue(OperationState.error("Failed to complete profile: "
                                + exception.getMessage()));
                    }
                });
    }
}
