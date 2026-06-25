package com.appname.dqra;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.appname.dqra.maindashboards.AdminDashboard;
import com.appname.dqra.maindashboards.UserDashboard;
import com.appname.dqra.model.StartDestination;
import com.appname.dqra.viewmodel.ProfileCheckViewModel;

public class ProfileCheckActivity extends AppCompatActivity {

    private ProfileCheckViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ProfileCheckViewModel.class);
        observeViewModel();
        viewModel.resolveDestination();
    }

    private void observeViewModel() {
        viewModel.getDestination().observe(this, this::navigateToDestination);
        viewModel.getError().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToDestination(StartDestination destination) {
        if (destination == null) {
            return;
        }

        Intent intent;
        switch (destination) {
            case MAIN_ADMIN:
                intent = new Intent(this, ISUFSTMAIN.class);
                break;
            case ADMIN_DASHBOARD:
                intent = new Intent(this, AdminDashboard.class);
                break;
            case COMPLETE_PROFILE:
                intent = new Intent(this, CompleteProfileActivity.class);
                break;
            case USER_DASHBOARD:
                intent = new Intent(this, UserDashboard.class);
                break;
            case LOGIN:
            default:
                intent = new Intent(this, LoginActivity.class);
                break;
        }

        startActivity(intent);
        finish();
    }
}
