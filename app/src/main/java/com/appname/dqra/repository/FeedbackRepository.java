package com.appname.dqra.repository;

import com.appname.dqra.model.AppFeedback;
import com.appname.dqra.security.AesCrypto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedbackRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    private static final String FEEDBACK_COLLECTION = "app_feedback";
    private static final String USERS_COLLECTION = "users";
    private static final String ADMINS_COLLECTION = "admins_with_stations";
    private static final String MAIN_COLLECTION = "main";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public FeedbackRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    FeedbackRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.auth = auth;
        this.firestore = firestore;
    }

    public void submitFeedback(String message, int rating, Callback<Void> callback) {
        String uid = currentUserId();
        if (uid == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        long weekStart = startOfWeekMillis();
        firestore.collection(FEEDBACK_COLLECTION)
                .whereEqualTo("senderUid", uid)
                .whereEqualTo("weekStart", weekStart)
                .get()
                .addOnSuccessListener(existing -> {
                    if (!existing.isEmpty()) {
                        callback.onError(new IllegalStateException(
                                "You can only send feedback once per week."));
                        return;
                    }

                    resolveSenderInfo(uid, new Callback<AppFeedback>() {
                        @Override
                        public void onSuccess(AppFeedback senderInfo) {
                            saveFeedback(senderInfo, message, rating, weekStart, callback);
                        }

                        @Override
                        public void onError(Exception exception) {
                            callback.onError(exception);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchFeedback(Callback<List<AppFeedback>> callback) {
        firestore.collection(FEEDBACK_COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AppFeedback> feedbackList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        AppFeedback feedback = document.toObject(AppFeedback.class);
                        feedback.setId(document.getId());
                        feedbackList.add(feedback);
                    }
                    callback.onSuccess(feedbackList);
                })
                .addOnFailureListener(callback::onError);
    }

    private void saveFeedback(AppFeedback senderInfo, String message, int rating,
                              long weekStart, Callback<Void> callback) {
        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("senderUid", senderInfo.getSenderUid());
        data.put("senderName", senderInfo.getSenderName());
        data.put("senderRole", senderInfo.getSenderRole());
        data.put("municipality", senderInfo.getMunicipality());
        data.put("message", message);
        data.put("rating", rating);
        data.put("timestamp", now);
        data.put("weekStart", weekStart);

        firestore.collection(FEEDBACK_COLLECTION)
                .add(data)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private void resolveSenderInfo(String uid, Callback<AppFeedback> callback) {
        firestore.collection(ADMINS_COLLECTION).document(uid).get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        callback.onSuccess(feedbackSender(uid,
                                firstNonEmpty(
                                        AesCrypto.decrypt(adminDoc.getString("stationName")),
                                        AesCrypto.decrypt(adminDoc.getString("email")),
                                        "Admin"),
                                "Admin",
                                firstNonEmpty(adminDoc.getString("municipality"), "")));
                        return;
                    }
                    resolveMainOrUserInfo(uid, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void resolveMainOrUserInfo(String uid, Callback<AppFeedback> callback) {
        firestore.collection(MAIN_COLLECTION).document(uid).get()
                .addOnSuccessListener(mainDoc -> {
                    if (mainDoc.exists()) {
                        callback.onSuccess(feedbackSender(uid,
                                firstNonEmpty(mainDoc.getString("name"), mainDoc.getString("email"), "ISUFST Main"),
                                "Main",
                                ""));
                        return;
                    }
                    resolveUserInfo(uid, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void resolveUserInfo(String uid, Callback<AppFeedback> callback) {
        firestore.collection(USERS_COLLECTION).document(uid).get()
                .addOnSuccessListener(userDoc -> callback.onSuccess(feedbackSender(uid,
                        firstNonEmpty(
                                AesCrypto.decrypt(userDoc.getString("fullName")),
                                AesCrypto.decrypt(userDoc.getString("email")),
                                "User"),
                        "User",
                        firstNonEmpty(
                                userDoc.getString("municipality"),
                                AesCrypto.decrypt(userDoc.getString("address")),
                                ""))))
                .addOnFailureListener(callback::onError);
    }

    private AppFeedback feedbackSender(String uid, String name, String role, String municipality) {
        return new AppFeedback(uid, name, role, municipality, "", 0, 0, 0);
    }

    private long startOfWeekMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String currentUserId() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
