package com.appname.dqra.repository;

import com.appname.dqra.model.StartDestination;
import com.appname.dqra.model.UserProfile;
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

        firestore.collection(USERS_COLLECTION).document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError(new IllegalStateException("User data not found"));
                        return;
                    }
                    callback.onSuccess(profileFromDocument(documentSnapshot));
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
        updates.put("fullName", fullName);
        updates.put("phone", phone);
        updates.put("address", address);

        firestore.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
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
                .addOnSuccessListener(unused -> callback.onSuccess(null))
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
                .addOnSuccessListener(unused -> callback.onSuccess(null))
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
