package com.appname.dqra.repository;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminRegistrationRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    private static final String ADMINS_COLLECTION = "admins_with_stations";
    private static final String ADMIN_REGISTRATION_APP_NAME = "AdminRegistrationApp";

    private final Context context;
    private final FirebaseFirestore firestore;
    private FirebaseAuth adminRegistrationAuth;

    public AdminRegistrationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void isAdminEmailAvailable(String email, Callback<Boolean> callback) {
        firestore.collection(ADMINS_COLLECTION)
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(snapshot.isEmpty()))
                .addOnFailureListener(callback::onError);
    }

    public void registerAdminWithStation(String email, String password, String municipality,
                                         String stationName, String stationPhone,
                                         String stationPhone2, String facebookPage,
                                         double latitude, double longitude,
                                         Callback<Void> callback) {
        FirebaseAuth registrationAuth = getAdminRegistrationAuth();
        registrationAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser registeredAdmin = registrationAuth.getCurrentUser();
                    if (registeredAdmin == null) {
                        callback.onError(new IllegalStateException("Admin account was not created correctly"));
                        return;
                    }

                    Map<String, Object> adminWithStationData = new HashMap<>();
                    adminWithStationData.put("adminId", registeredAdmin.getUid());
                    adminWithStationData.put("email", email);
                    adminWithStationData.put("role", "Admin");
                    adminWithStationData.put("municipality", municipality);
                    adminWithStationData.put("stationName", stationName);
                    adminWithStationData.put("stationPhone", stationPhone);
                    adminWithStationData.put("stationPhone2", stationPhone2);
                    adminWithStationData.put("facebookPage", facebookPage);
                    adminWithStationData.put("latitude", latitude);
                    adminWithStationData.put("longitude", longitude);
                    adminWithStationData.put("isVerified", false);

                    firestore.collection(ADMINS_COLLECTION).document(registeredAdmin.getUid())
                            .set(adminWithStationData)
                            .addOnSuccessListener(unused -> {
                                registrationAuth.signOut();
                                callback.onSuccess(null);
                            })
                            .addOnFailureListener(exception -> {
                                registrationAuth.signOut();
                                callback.onError(exception);
                            });
                })
                .addOnFailureListener(exception -> {
                    registrationAuth.signOut();
                    callback.onError(exception);
                });
    }

    private FirebaseAuth getAdminRegistrationAuth() {
        if (adminRegistrationAuth != null) {
            return adminRegistrationAuth;
        }

        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(ADMIN_REGISTRATION_APP_NAME);
        } catch (IllegalStateException ignored) {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            app = FirebaseApp.initializeApp(context, options, ADMIN_REGISTRATION_APP_NAME);
        }

        adminRegistrationAuth = FirebaseAuth.getInstance(app);
        return adminRegistrationAuth;
    }
}
