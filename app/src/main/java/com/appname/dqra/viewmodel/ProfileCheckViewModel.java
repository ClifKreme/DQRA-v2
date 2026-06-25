package com.appname.dqra.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.appname.dqra.model.StartDestination;
import com.appname.dqra.repository.AccountRepository;

public class ProfileCheckViewModel extends ViewModel {

    private final AccountRepository repository;
    private final MutableLiveData<StartDestination> destination = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ProfileCheckViewModel() {
        this(new AccountRepository());
    }

    ProfileCheckViewModel(AccountRepository repository) {
        this.repository = repository;
    }

    public LiveData<StartDestination> getDestination() {
        return destination;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void resolveDestination() {
        repository.resolveStartDestination(new AccountRepository.Callback<StartDestination>() {
            @Override
            public void onSuccess(StartDestination value) {
                destination.setValue(value);
            }

            @Override
            public void onError(Exception exception) {
                error.setValue(exception.getMessage());
                destination.setValue(StartDestination.LOGIN);
            }
        });
    }
}
