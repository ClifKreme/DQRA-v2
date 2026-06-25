package com.appname.dqra.repository;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class WebsiteRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    private static final String WEBSITE_NOTIFICATIONS_COLLECTION = "website_notifications";

    private final FirebaseFirestore firestore;

    public WebsiteRepository() {
        this(FirebaseFirestore.getInstance());
    }

    WebsiteRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void sendWebsiteAccessNotification(String municipality, Callback<Void> callback) {
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "website_access");
        notificationData.put("timestamp", FieldValue.serverTimestamp());
        notificationData.put("municipality", municipality);
        notificationData.put("status", "unread");

        firestore.collection(WEBSITE_NOTIFICATIONS_COLLECTION)
                .add(notificationData)
                .addOnSuccessListener(ref -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
