package com.appname.dqra.repository;

import android.content.Context;

import com.appname.dqra.model.UserRegistrationRequest;
import com.appname.dqra.security.AesCrypto;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRegistrationRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    public interface RequestListener {
        void onRequestsChanged(List<UserRegistrationRequest> requests);
        void onError(Exception exception);
    }

    public interface StatusListener {
        void onStatusChanged(String status);
        void onError(Exception exception);
    }

    private static final String REGISTRATION_APP_NAME = "UserRegistrationApp";
    private static final String REQUESTS_COLLECTION = "user_registration_requests";
    private static final String USERS_COLLECTION = "users";

    private final Context context;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth defaultAuth;
    private FirebaseAuth registrationAuth;
    private FirebaseFirestore registrationFirestore;

    public UserRegistrationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.firestore = FirebaseFirestore.getInstance();
        this.defaultAuth = FirebaseAuth.getInstance();
    }

    public void submitRegistration(String fullName, String email, String password, String phone,
                                   String address, String selfieImageBase64,
                                   String nationalIdFrontBase64, String nationalIdBackBase64,
                                   Callback<Void> callback) {
        FirebaseAuth auth = getRegistrationAuth();
        FirebaseFirestore requestFirestore = getRegistrationFirestore();
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError(new IllegalStateException("User account was not created correctly"));
                        return;
                    }

                    user.getIdToken(true)
                            .addOnSuccessListener(tokenResult -> {
                                Map<String, Object> request = new HashMap<>();
                                request.put("userId", user.getUid());
                                request.put("fullName", AesCrypto.encrypt(fullName));
                                request.put("email", AesCrypto.encrypt(email));
                                request.put("emailHash", AesCrypto.sha256Hex(email));
                                request.put("phone", AesCrypto.encrypt(phone));
                                request.put("address", AesCrypto.encrypt(address));
                                request.put("selfieImageBase64", selfieImageBase64);
                                request.put("nationalIdImageBase64", nationalIdFrontBase64);
                                request.put("nationalIdFrontBase64", nationalIdFrontBase64);
                                request.put("nationalIdBackBase64", nationalIdBackBase64);
                                request.put("status", "pending");
                                request.put("submittedAt", System.currentTimeMillis());

                                requestFirestore.collection(REQUESTS_COLLECTION).document(user.getUid())
                                        .set(request)
                                        .addOnSuccessListener(unused -> {
                                            auth.signOut();
                                            defaultAuth.signInWithEmailAndPassword(email, password)
                                                    .addOnSuccessListener(defaultAuthResult -> callback.onSuccess(null))
                                                    .addOnFailureListener(callback::onError);
                                        })
                                        .addOnFailureListener(exception -> {
                                            auth.signOut();
                                            callback.onError(exception);
                                        });
                            })
                            .addOnFailureListener(exception -> {
                                auth.signOut();
                                callback.onError(exception);
                            });
                })
                .addOnFailureListener(exception -> {
                    auth.signOut();
                    callback.onError(exception);
                });
    }

    public ListenerRegistration listenForPendingRequests(RequestListener listener) {
        return firestore.collection(REQUESTS_COLLECTION)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, exception) -> {
                    if (exception != null) {
                        listener.onError(exception);
                        return;
                    }

                    List<UserRegistrationRequest> requests = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            requests.add(requestFromDocument(document));
                        }
                    }
                    Collections.sort(requests,
                            (first, second) -> Long.compare(second.getSubmittedAt(), first.getSubmittedAt()));
                    listener.onRequestsChanged(requests);
                });
    }

    public ListenerRegistration listenForCurrentUserRequestStatus(StatusListener listener) {
        FirebaseUser user = defaultAuth.getCurrentUser();
        if (user == null) {
            listener.onError(new IllegalStateException("User is not signed in"));
            return null;
        }

        return firestore.collection(REQUESTS_COLLECTION).document(user.getUid())
                .addSnapshotListener((snapshot, exception) -> {
                    if (exception != null) {
                        listener.onError(exception);
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        listener.onStatusChanged("missing");
                        return;
                    }
                    listener.onStatusChanged(snapshot.getString("status"));
                });
    }

    public void signOut() {
        defaultAuth.signOut();
    }

    public void approveRequest(UserRegistrationRequest request, String adminId, Callback<Void> callback) {
        Map<String, Object> user = new HashMap<>();
        user.put("role", "User");
        user.put("email", AesCrypto.encrypt(request.getEmail()));
        user.put("emailHash", AesCrypto.sha256Hex(request.getEmail()));
        user.put("fullName", AesCrypto.encrypt(request.getFullName()));
        user.put("phone", AesCrypto.encrypt(request.getPhone()));
        user.put("address", AesCrypto.encrypt(request.getAddress()));
        user.put("profileCompleted", true);
        user.put("profileImageBase64", request.getSelfieImageBase64());
        user.put("createdAt", System.currentTimeMillis());
        user.put("approvedAt", System.currentTimeMillis());
        user.put("approvedBy", adminId);

        Map<String, Object> requestUpdates = new HashMap<>();
        requestUpdates.put("status", "approved");
        requestUpdates.put("reviewedAt", System.currentTimeMillis());
        requestUpdates.put("reviewedBy", adminId);

        firestore.collection(USERS_COLLECTION).document(request.getId()).set(user)
                .addOnSuccessListener(unused -> firestore.collection(REQUESTS_COLLECTION)
                        .document(request.getId())
                        .update(requestUpdates)
                        .addOnSuccessListener(done -> callback.onSuccess(null))
                        .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    public void rejectRequest(UserRegistrationRequest request, String adminId, Callback<Void> callback) {
        Map<String, Object> requestUpdates = new HashMap<>();
        requestUpdates.put("status", "rejected");
        requestUpdates.put("reviewedAt", System.currentTimeMillis());
        requestUpdates.put("reviewedBy", adminId);

        firestore.collection(REQUESTS_COLLECTION).document(request.getId())
                .update(requestUpdates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private UserRegistrationRequest requestFromDocument(DocumentSnapshot document) {
        Long submittedAt = document.getLong("submittedAt");
        // Support both old single-field and new front/back field format
        String nationalIdFront = document.getString("nationalIdFrontBase64");
        if (nationalIdFront == null) {
            nationalIdFront = document.getString("nationalIdImageBase64");
        }
        String nationalIdBack = document.getString("nationalIdBackBase64");
        return new UserRegistrationRequest(
                document.getId(),
                AesCrypto.decrypt(document.getString("fullName")),
                AesCrypto.decrypt(document.getString("email")),
                AesCrypto.decrypt(document.getString("phone")),
                AesCrypto.decrypt(document.getString("address")),
                document.getString("selfieImageBase64"),
                nationalIdFront,
                nationalIdBack,
                document.getString("status"),
                submittedAt != null ? submittedAt : 0L);
    }

    private FirebaseAuth getRegistrationAuth() {
        if (registrationAuth != null) {
            return registrationAuth;
        }

        FirebaseApp app = getRegistrationApp();
        registrationAuth = FirebaseAuth.getInstance(app);
        return registrationAuth;
    }

    private FirebaseFirestore getRegistrationFirestore() {
        if (registrationFirestore != null) {
            return registrationFirestore;
        }

        registrationFirestore = FirebaseFirestore.getInstance(getRegistrationApp());
        return registrationFirestore;
    }

    private FirebaseApp getRegistrationApp() {
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(REGISTRATION_APP_NAME);
        } catch (IllegalStateException ignored) {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            app = FirebaseApp.initializeApp(context, options, REGISTRATION_APP_NAME);
        }
        return app;
    }
}
