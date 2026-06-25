package com.appname.dqra.repository;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.model.StartDestination;
import com.appname.dqra.model.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
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

    public void loginWithGoogleToken(String idToken, Callback<Boolean> callback) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("Authentication failed"));
                        return;
                    }

                    firestore.collection(USERS_COLLECTION).document(user.getUid()).get()
                            .addOnSuccessListener(document -> callback.onSuccess(!document.exists()))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void createGoogleUser(Callback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("role", "User");
        userMap.put("email", user.getEmail());
        userMap.put("termsAccepted", true);
        userMap.put("createdAt", System.currentTimeMillis());

        firestore.collection(USERS_COLLECTION).document(user.getUid()).set(userMap)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
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
                                                        callback.onError(new IllegalStateException("User account not found"));
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

    public void registerUser(String name, String email, String password, String phone, String address,
                             Callback<Void> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String userId = getCurrentUserId();
                    if (userId == null) {
                        callback.onError(new IllegalStateException("User account was not created correctly"));
                        return;
                    }

                    Map<String, Object> user = new HashMap<>();
                    user.put("id", userId);
                    user.put("fullName", name);
                    user.put("email", email);
                    user.put("phone", phone);
                    user.put("address", address);
                    user.put("role", "User");

                    firestore.collection(USERS_COLLECTION)
                            .document(userId)
                            .set(user)
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
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
                .addOnFailureListener(callback::onError);
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
                .addOnFailureListener(callback::onError);
    }

    private void resolveUserDestination(String userId, Callback<StartDestination> callback) {
        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        signOut();
                        callback.onSuccess(StartDestination.LOGIN);
                        return;
                    }

                    UserProfile profile = profileFromDocument(userDoc);
                    callback.onSuccess(profile.isComplete()
                            ? StartDestination.USER_DASHBOARD
                            : StartDestination.COMPLETE_PROFILE);
                })
                .addOnFailureListener(callback::onError);
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

    public void updateCurrentUserProfile(String fullName, String phone, String address,
                                         Callback<Void> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("phone", phone);
        updates.put("address", address);

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

    public void updateCurrentUserProfileImage(String profileImageBase64, Callback<Void> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("profileImageBase64", profileImageBase64);

        firestore.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
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
                                        profileImageBase64));
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
        updates.put("fullName", fullName);
        updates.put("phone", phone);
        updates.put("address", address);
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
        return new UserProfile(
                document.getId(),
                document.getString("fullName"),
                document.getString("email"),
                document.getString("phone"),
                document.getString("address"),
                document.getString("role"),
                document.getString("profileImageBase64"));
    }
}
