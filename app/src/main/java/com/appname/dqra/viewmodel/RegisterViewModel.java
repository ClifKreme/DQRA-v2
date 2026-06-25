package com.appname.dqra.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.repository.AccountRepository;

public class RegisterViewModel extends ViewModel {

    private final AccountRepository repository;
    private final MutableLiveData<OperationState> state =
            new MutableLiveData<>(OperationState.idle());

    public RegisterViewModel() {
        this(new AccountRepository());
    }

    RegisterViewModel(AccountRepository repository) {
        this.repository = repository;
    }

    public LiveData<OperationState> getState() {
        return state;
    }

    public void registerUser(String name, String email, String password, String phone, String address) {
        state.setValue(OperationState.loading());
        repository.registerUser(name, email, password, phone, address,
                new AccountRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        state.setValue(OperationState.success("User Registered Successfully!"));
                    }

                    @Override
                    public void onError(Exception exception) {
                        state.setValue(OperationState.error("Error: " + exception.getMessage()));
                    }
                });
    }
}
