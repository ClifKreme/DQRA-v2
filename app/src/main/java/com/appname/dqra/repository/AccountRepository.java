package com.appname.dqra.repository;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.model.StartDestination;
import com.appname.dqra.model.UserProfile;
import com.appname.dqra.security.AesCrypto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AccountRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    private static final String USERS_COLLECTION = "users";
    private static final String ADMINS_COLLECTION = "admins_with_stations";
    private static final String MAIN_COLLECTION = "main";
    private static final String REGISTRATION_REQUESTS_COLLECTION = "user_registration_requests";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public AccountRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    AccountRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.auth = auth;
        this.firestore = firestore;
    }

    public boolean hasCurrentUser() {
        return auth.getCurrentUser() != null;
    }

    public String getCurrentUserId() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }

    public void signOut() {
        auth.signOut();
    }

    public String getCurrentUserEmail() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getEmail();
    }

    public void loginWithEmail(String email, String password, Callback<Void> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void resolveLoginRole(Callback<StartDestination> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        firestore.collection(MAIN_COLLECTION).document(userId).get()
                .addOnSuccessListener(mainDoc -> {
                    if (mainDoc.exists() && "main".equalsIgnoreCase(mainDoc.getString("role"))) {
                        callback.onSuccess(StartDestination.MAIN_ADMIN);
                    } else {
                        firestore.collection(USERS_COLLECTION).document(userId).get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        callback.onSuccess(StartDestination.USER_DASHBOARD);
                                    } else {
                                        firestore.collection(ADMINS_COLLECTION).document(userId).get()
                                                .addOnSuccessListener(adminDoc -> {
                                                    if (adminDoc.exists()) {
                                                        callback.onSuccess(StartDestination.ADMIN_DASHBOARD);
                                                    } else {
                                                        resolvePendingRegistration(userId, callback);
                                                    }
                                                })
                                                .addOnFailureListener(callback::onError);
                                    }
                                })
                                .addOnFailureListener(callback::onError);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void resolvePendingRegistration(String userId, Callback<StartDestination> callback) {
        firestore.collection(REGISTRATION_REQUESTS_COLLECTION).document(userId).get()
                .addOnSuccessListener(requestDoc -> {
                    if (!requestDoc.exists()) {
                        callback.onError(new IllegalStateException("User account not found"));
                        return;
                    }

                    String status = requestDoc.getString("status");
                    if ("pending".equalsIgnoreCase(status)) {
                        callback.onSuccess(StartDestination.PENDING_APPROVAL);
                    } else if ("rejected".equalsIgnoreCase(status)) {
                        signOut();
                        callback.onError(new IllegalStateException(
                                "Your registration request was rejected. Please contact an admin or submit again."));
                    } else {
                        callback.onSuccess(StartDestination.USER_DASHBOARD);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void resolveStartDestination(Callback<StartDestination> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onSuccess(StartDestination.LOGIN);
            return;
        }

        firestore.collection(MAIN_COLLECTION).document(userId).get()
                .addOnSuccessListener(mainDoc -> {
                    String role = mainDoc.getString("role");
                    if (mainDoc.exists() && "main".equalsIgnoreCase(role)) {
                        callback.onSuccess(StartDestination.MAIN_ADMIN);
                        return;
                    }
                    resolveAdminOrUserDestination(userId, callback);
                })
                .addOnFailureListener(exception -> resolveCachedStartDestination(userId, callback, exception));
    }

    private void resolveAdminOrUserDestination(String userId, Callback<StartDestination> callback) {
        firestore.collection(ADMINS_COLLECTION).document(userId).get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        callback.onSuccess(StartDestination.ADMIN_DASHBOARD);
                        return;
                    }
                    resolveUserDestination(userId, callback);
                })
                .addOnFailureListener(exception -> resolveCachedStartDestination(userId, callback, exception));
    }

    private void resolveUserDestination(String userId, Callback<StartDestination> callback) {
        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        resolvePendingStartDestination(userId, callback);
                        return;
                    }

                    UserProfile profile = profileFromDocument(userDoc);
                    callback.onSuccess(profile.isComplete()
                            ? StartDestination.USER_DASHBOARD
                            : StartDestination.COMPLETE_PROFILE);
                })
                .addOnFailureListener(exception -> resolveCachedStartDestination(userId, callback, exception));
    }

    private void resolvePendingStartDestination(String userId, Callback<StartDestination> callback) {
        firestore.collection(REGISTRATION_REQUESTS_COLLECTION).document(userId).get()
                .addOnSuccessListener(requestDoc -> {
                    if (!requestDoc.exists()) {
                        signOut();
                        callback.onSuccess(StartDestination.LOGIN);
                        return;
                    }

                    String status = requestDoc.getString("status");
                    if ("pending".equalsIgnoreCase(status)) {
                        callback.onSuccess(StartDestination.PENDING_APPROVAL);
                    } else if ("approved".equalsIgnoreCase(status)) {
                        callback.onSuccess(StartDestination.USER_DASHBOARD);
                    } else {
                        signOut();
                        callback.onSuccess(StartDestination.LOGIN);
                    }
                })
                .addOnFailureListener(exception -> resolveCachedStartDestination(userId, callback, exception));
    }

    private void resolveCachedStartDestination(String userId, Callback<StartDestination> callback,
                                               Exception originalException) {
        UserProfile cachedUserProfile = AppCache.getInstance().getUserProfile(userId);
        if (cachedUserProfile != null) {
            callback.onSuccess(cachedUserProfile.isComplete()
                    ? StartDestination.USER_DASHBOARD
                    : StartDestination.COMPLETE_PROFILE);
            return;
        }

        if (AppCache.getInstance().getAdminProfile(userId) != null
                || AppCache.getInstance().getAdminMunicipality(userId) != null) {
            callback.onSuccess(StartDestination.ADMIN_DASHBOARD);
            return;
        }

        callback.onError(originalException);
    }

    public void loadCurrentUserProfile(Callback<UserProfile> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        UserProfile cachedProfile = AppCache.getInstance().getUserProfile(userId);
        boolean hasCache = cachedProfile != null;
        if (hasCache) {
            callback.onSuccess(cachedProfile);
        }

        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        if (!hasCache) {
                            callback.onError(new IllegalStateException("User data not found"));
                        }
                        return;
                    }
                    UserProfile profile = profileFromDocument(documentSnapshot);
                    AppCache.getInstance().putUserProfile(userId, profile);
                    callback.onSuccess(profile);
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    public void loadCurrentUserProfileFresh(Callback<UserProfile> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError(new IllegalStateException("User data not found"));
                        return;
                    }

                    UserProfile profile = profileFromDocument(documentSnapshot);
                    AppCache.getInstance().putUserProfile(userId, profile);
                    callback.onSuccess(profile);
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateCurrentUserProfile(String fullName, String phone, String address,
                                         Callback<Void> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", AesCrypto.encrypt(fullName));
        updates.put("phone", AesCrypto.encrypt(phone));
        updates.put("address", AesCrypto.encrypt(address));
        updates.put("profileLastUpdatedAt", System.currentTimeMillis());

        firestore.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    long now = System.currentTimeMillis();
                    UserProfile cachedProfile = AppCache.getInstance().getUserProfile(userId);
                    String email = cachedProfile == null ? null : cachedProfile.getEmail();
                    String role = cachedProfile == null ? null : cachedProfile.getRole();
                    String profileImage = cachedProfile == null ? null : cachedProfile.getProfileImageBase64();
                    long photoTs = cachedProfile == null ? 0L : cachedProfile.getPhotoLastUpdatedAt();
                    AppCache.getInstance().putUserProfile(userId,
                            new UserProfile(userId, fullName, email, phone, address, role, profileImage, now, photoTs));
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateCurrentUserProfileImage(String profileImageBase64, Callback<Void> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("profileImageBase64", profileImageBase64);
        updates.put("photoLastUpdatedAt", System.currentTimeMillis());

        firestore.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    long now = System.currentTimeMillis();
                    UserProfile cachedProfile = AppCache.getInstance().getUserProfile(userId);
                    if (cachedProfile != null) {
                        AppCache.getInstance().putUserProfile(userId,
                                new UserProfile(
                                        cachedProfile.getId(),
                                        cachedProfile.getFullName(),
                                        cachedProfile.getEmail(),
                                        cachedProfile.getPhone(),
                                        cachedProfile.getAddress(),
                                        cachedProfile.getRole(),
                                        profileImageBase64,
                                        cachedProfile.getProfileLastUpdatedAt(),
                                        now));
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void completeCurrentUserProfile(String fullName, String phone, String address,
                                           Callback<Void> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", AesCrypto.encrypt(fullName));
        updates.put("phone", AesCrypto.encrypt(phone));
        updates.put("address", AesCrypto.encrypt(address));
        updates.put("profileCompleted", true);
        updates.put("profileCompletedAt", System.currentTimeMillis());

        firestore.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    UserProfile cachedProfile = AppCache.getInstance().getUserProfile(userId);
                    String email = cachedProfile == null ? null : cachedProfile.getEmail();
                    String role = cachedProfile == null ? null : cachedProfile.getRole();
                    String profileImage = cachedProfile == null ? null : cachedProfile.getProfileImageBase64();
                    AppCache.getInstance().putUserProfile(userId,
                            new UserProfile(userId, fullName, email, phone, address, role, profileImage));
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    private UserProfile profileFromDocument(DocumentSnapshot document) {
        Long lastUpdated = document.getLong("profileLastUpdatedAt");
        Long photoUpdated = document.getLong("photoLastUpdatedAt");
        return new UserProfile(
                document.getId(),
                AesCrypto.decrypt(document.getString("fullName")),
                AesCrypto.decrypt(document.getString("email")),
                AesCrypto.decrypt(document.getString("phone")),
                AesCrypto.decrypt(document.getString("address")),
                document.getString("role"),
                document.getString("profileImageBase64"),
                lastUpdated != null ? lastUpdated : 0L,
                photoUpdated != null ? photoUpdated : 0L);
    }
}
