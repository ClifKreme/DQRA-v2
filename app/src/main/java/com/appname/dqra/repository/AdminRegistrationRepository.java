package com.appname.dqra.repository;

import android.content.Context;

import com.appname.dqra.security.AesCrypto;
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
    private FirebaseFirestore adminRegistrationFirestore;

    public AdminRegistrationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public void isAdminEmailAvailable(String email, Callback<Boolean> callback) {
        firestore.collection(ADMINS_COLLECTION)
                .whereEqualTo("emailHash", AesCrypto.sha256Hex(email))
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        callback.onSuccess(false);
                        return;
                    }
                    firestore.collection(ADMINS_COLLECTION)
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener(legacySnapshot -> callback.onSuccess(legacySnapshot.isEmpty()))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void registerAdminWithStation(String email, String password, String municipality,
                                         String stationName, String stationPhone,
                                         String stationPhone2, String facebookPage,
                                         double latitude, double longitude,
                                         Callback<Void> callback) {
        FirebaseAuth registrationAuth = getAdminRegistrationAuth();
        FirebaseFirestore registrationFirestore = getAdminRegistrationFirestore();
        registrationAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser registeredAdmin = registrationAuth.getCurrentUser();
                    if (registeredAdmin == null) {
                        callback.onError(new IllegalStateException("Admin account was not created correctly"));
                        return;
                    }

                    registeredAdmin.getIdToken(true)
                            .addOnSuccessListener(tokenResult -> {
                                Map<String, Object> adminWithStationData = new HashMap<>();
                                adminWithStationData.put("adminId", registeredAdmin.getUid());
                                adminWithStationData.put("email", AesCrypto.encrypt(email));
                                adminWithStationData.put("emailHash", AesCrypto.sha256Hex(email));
                                adminWithStationData.put("role", "Admin");
                                adminWithStationData.put("municipality", municipality);
                                adminWithStationData.put("stationName", AesCrypto.encrypt(stationName));
                                adminWithStationData.put("stationPhone", AesCrypto.encrypt(stationPhone));
                                adminWithStationData.put("stationPhone2", AesCrypto.encrypt(stationPhone2));
                                adminWithStationData.put("facebookPage", AesCrypto.encrypt(facebookPage));
                                adminWithStationData.put("latitude", latitude);
                                adminWithStationData.put("longitude", longitude);
                                adminWithStationData.put("isVerified", false);

                                registrationFirestore.collection(ADMINS_COLLECTION)
                                        .document(registeredAdmin.getUid())
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

    private FirebaseFirestore getAdminRegistrationFirestore() {
        if (adminRegistrationFirestore != null) {
            return adminRegistrationFirestore;
        }

        adminRegistrationFirestore = FirebaseFirestore.getInstance(getAdminRegistrationApp());
        return adminRegistrationFirestore;
    }

    private FirebaseApp getAdminRegistrationApp() {
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(ADMIN_REGISTRATION_APP_NAME);
        } catch (IllegalStateException ignored) {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            app = FirebaseApp.initializeApp(context, options, ADMIN_REGISTRATION_APP_NAME);
        }
        return app;
    }
}
