package com.appname.dqra.repository;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.model.AdminProfile;
import com.appname.dqra.security.AesCrypto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminProfileRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    private static final String ADMINS_COLLECTION = "admins_with_stations";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public AdminProfileRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    AdminProfileRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.auth = auth;
        this.firestore = firestore;
    }

    public void signOut() {
        auth.signOut();
    }

    public void loadCurrentAdminProfile(Callback<AdminProfile> callback) {
        String userId = currentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("Admin is not signed in"));
            return;
        }

        AdminProfile cachedProfile = AppCache.getInstance().getAdminProfile(userId);
        boolean hasCache = cachedProfile != null;
        if (hasCache) {
            callback.onSuccess(cachedProfile);
        }

        firestore.collection(ADMINS_COLLECTION).document(userId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        if (!hasCache) {
                            callback.onError(new IllegalStateException("Admin profile not found"));
                        }
                        return;
                    }

                    Long lastUpdated = document.getLong("profileLastUpdatedAt");
                    Long photoUpdated = document.getLong("photoLastUpdatedAt");
                    AdminProfile profile = new AdminProfile(
                            AesCrypto.decrypt(document.getString("stationName")),
                            document.getString("municipality"),
                            AesCrypto.decrypt(document.getString("stationPhone")),
                            AesCrypto.decrypt(document.getString("email")),
                            document.getString("profileImageBase64"),
                            lastUpdated != null ? lastUpdated : 0L,
                            photoUpdated != null ? photoUpdated : 0L);
                    AppCache.getInstance().putAdminProfile(userId, profile);
                    AppCache.getInstance().putAdminMunicipality(userId, profile.getMunicipality());
                    callback.onSuccess(profile);
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    public void updateCurrentAdminProfile(String stationName, String municipality,
                                          String stationPhone, Callback<Void> callback) {
        String userId = currentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("Admin is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("stationName", AesCrypto.encrypt(stationName));
        updates.put("municipality", municipality);
        updates.put("stationPhone", AesCrypto.encrypt(stationPhone));
        updates.put("profileLastUpdatedAt", System.currentTimeMillis());

        firestore.collection(ADMINS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    long now = System.currentTimeMillis();
                    AdminProfile cachedProfile = AppCache.getInstance().getAdminProfile(userId);
                    String email = cachedProfile == null ? null : cachedProfile.getEmail();
                    String profileImage = cachedProfile == null ? null : cachedProfile.getProfileImageBase64();
                    long photoTs = cachedProfile == null ? 0L : cachedProfile.getPhotoLastUpdatedAt();
                    AdminProfile profile = new AdminProfile(stationName, municipality,
                            stationPhone, email, profileImage, now, photoTs);
                    AppCache.getInstance().putAdminProfile(userId, profile);
                    AppCache.getInstance().putAdminMunicipality(userId, municipality);
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateCurrentAdminProfileImage(String profileImageBase64, Callback<Void> callback) {
        String userId = currentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("Admin is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("profileImageBase64", profileImageBase64);
        updates.put("photoLastUpdatedAt", System.currentTimeMillis());

        firestore.collection(ADMINS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    long now = System.currentTimeMillis();
                    AdminProfile cachedProfile = AppCache.getInstance().getAdminProfile(userId);
                    if (cachedProfile != null) {
                        AppCache.getInstance().putAdminProfile(userId,
                                new AdminProfile(
                                        cachedProfile.getStationName(),
                                        cachedProfile.getMunicipality(),
                                        cachedProfile.getStationPhone(),
                                        cachedProfile.getEmail(),
                                        profileImageBase64,
                                        cachedProfile.getProfileLastUpdatedAt(),
                                        now));
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    private String currentUserId() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }
}
