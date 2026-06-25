package com.appname.dqra.repository;

import android.location.Location;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.viewmodel.AdminEmergency;
import com.appname.dqra.viewmodel.AdminEmergencyStats;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class AdminDashboardRepository {

    public interface ValueCallback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    public interface EmergencyListener {
        void onEmpty();
        void onEmergency(AdminEmergency emergency);
        void onError(Exception exception);
    }

    public interface WebsiteNotificationListener {
        void onNotification();
        void onError(Exception exception);
    }

    private static final String ADMINS_COLLECTION = "admins_with_stations";
    private static final String EMERGENCIES_COLLECTION = "emergency_locations";
    private static final String WEBSITE_NOTIFICATIONS_COLLECTION = "website_notifications";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_RESOLVED = "resolved";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public AdminDashboardRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    AdminDashboardRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
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

    public void fetchAdminMunicipality(ValueCallback<String> callback) {
        String adminUid = getCurrentUserId();
        if (adminUid == null) {
            callback.onError(new IllegalStateException("Admin is not signed in"));
            return;
        }

        String cachedMunicipality = AppCache.getInstance().getAdminMunicipality(adminUid);
        boolean hasCache = cachedMunicipality != null && !cachedMunicipality.isEmpty();
        if (hasCache) {
            callback.onSuccess(cachedMunicipality);
        }

        firestore.collection(ADMINS_COLLECTION)
                .document(adminUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        if (!hasCache) {
                            callback.onError(new IllegalStateException(
                                    "Admin account not properly configured. Please contact system administrator."));
                        }
                        return;
                    }

                    String municipality = documentSnapshot.getString("municipality");
                    if (municipality == null || municipality.isEmpty()) {
                        if (!hasCache) {
                            callback.onError(new IllegalStateException(
                                    "No municipality assigned to admin. Please contact system administrator."));
                        }
                        return;
                    }

                    AppCache.getInstance().putAdminMunicipality(adminUid, municipality);
                    callback.onSuccess(municipality);
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    public ListenerRegistration listenForEmergencies(String municipality,
                                                     String currentEmergencyId,
                                                     EmergencyListener listener) {
        return firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("municipality", municipality)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        listener.onEmpty();
                        return;
                    }

                    QueryDocumentSnapshot mostRecentPendingEmergency = null;
                    QueryDocumentSnapshot currentEmergencyDoc = null;
                    long mostRecentTimestamp = 0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String emergencyId = document.getId();
                        long timestamp = getTimestampMillis(document);

                        boolean isOldEmergency = timestamp > 0
                                && System.currentTimeMillis() - timestamp > 10 * 60 * 60 * 1000;
                        if (isOldEmergency && !emergencyId.equals(currentEmergencyId)) {
                            continue;
                        }

                        String status = document.getString("status");
                        if (STATUS_PENDING.equals(status) && timestamp > mostRecentTimestamp) {
                            mostRecentTimestamp = timestamp;
                            mostRecentPendingEmergency = document;
                        }

                        if (emergencyId.equals(currentEmergencyId)) {
                            currentEmergencyDoc = document;
                        }
                    }

                    if (mostRecentPendingEmergency != null) {
                        listener.onEmergency(emergencyFromDocument(mostRecentPendingEmergency));
                    } else if (currentEmergencyDoc != null) {
                        listener.onEmergency(emergencyFromDocument(currentEmergencyDoc));
                    } else {
                        listener.onEmpty();
                    }
                });
    }

    public void fetchStats(String municipality, ValueCallback<AdminEmergencyStats> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("municipality", municipality)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int pendingCount = 0;
                    int activeCount = 0;
                    int resolvedCount = 0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String status = document.getString("status");
                        if (STATUS_PENDING.equals(status)) {
                            pendingCount++;
                        } else if (STATUS_CONFIRMED.equals(status)) {
                            activeCount++;
                        } else if (STATUS_RESOLVED.equals(status) || "completed".equals(status)) {
                            resolvedCount++;
                        }
                    }

                    callback.onSuccess(new AdminEmergencyStats(pendingCount, activeCount, resolvedCount));
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateEmergencyStatus(String emergencyId, String status, ValueCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);

        if (STATUS_CONFIRMED.equals(status)) {
            updates.put("confirmedAt", FieldValue.serverTimestamp());
        } else if (STATUS_REJECTED.equals(status)) {
            updates.put("rejectedAt", FieldValue.serverTimestamp());
        } else if (STATUS_RESOLVED.equals(status)) {
            updates.put("resolvedAt", FieldValue.serverTimestamp());
            String uid = getCurrentUserId();
            updates.put("resolvedBy", uid == null ? "unknown" : uid);
        }

        firestore.collection(EMERGENCIES_COLLECTION)
                .document(emergencyId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void updateAdminLocation(Location location, String municipality, ValueCallback<Void> callback) {
        String adminUid = getCurrentUserId();
        if (adminUid == null) {
            callback.onError(new IllegalStateException("Admin is not signed in"));
            return;
        }

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("timestamp", FieldValue.serverTimestamp());
        locationData.put("municipality", municipality);

        firestore.collection(ADMINS_COLLECTION)
                .document(adminUid)
                .set(locationData, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void fetchEmergencyImageData(String emergencyId, ValueCallback<AdminEmergency> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .document(emergencyId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError(new IllegalStateException("Emergency not found"));
                        return;
                    }

                    callback.onSuccess(emergencyFromSnapshot(
                            documentSnapshot.getId(),
                            documentSnapshot.getString("municipality"),
                            documentSnapshot.getDouble("latitude"),
                            documentSnapshot.getDouble("longitude"),
                            documentSnapshot.getString("phoneNumber"),
                            documentSnapshot.getString("reportedBy"),
                            documentSnapshot.getString("reportedByProfileImageBase64"),
                            documentSnapshot.getString("status"),
                            documentSnapshot.getBoolean("hasImage"),
                            documentSnapshot.getString("imageBase64"),
                            getTimestampMillis(documentSnapshot.get("timestamp"))));
                })
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration listenForEmergencyImageData(String emergencyId,
                                                            ValueCallback<AdminEmergency> callback) {
        return firestore.collection(EMERGENCIES_COLLECTION)
                .document(emergencyId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        callback.onError(new IllegalStateException("Emergency not found"));
                        return;
                    }

                    callback.onSuccess(emergencyFromSnapshot(
                            documentSnapshot.getId(),
                            documentSnapshot.getString("municipality"),
                            documentSnapshot.getDouble("latitude"),
                            documentSnapshot.getDouble("longitude"),
                            documentSnapshot.getString("phoneNumber"),
                            documentSnapshot.getString("reportedBy"),
                            documentSnapshot.getString("reportedByProfileImageBase64"),
                            documentSnapshot.getString("status"),
                            documentSnapshot.getBoolean("hasImage"),
                            documentSnapshot.getString("imageBase64"),
                            getTimestampMillis(documentSnapshot.get("timestamp"))));
                });
    }

    public ListenerRegistration listenForWebsiteNotifications(String municipality,
                                                              WebsiteNotificationListener listener) {
        return firestore.collection(WEBSITE_NOTIFICATIONS_COLLECTION)
                .whereEqualTo("municipality", municipality)
                .whereEqualTo("status", "unread")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        return;
                    }

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        listener.onNotification();
                        document.getReference().update("status", "read");
                    }
                });
    }

    private AdminEmergency emergencyFromDocument(QueryDocumentSnapshot document) {
        Double lat = document.getDouble("latitude");
        Double lng = document.getDouble("longitude");
        Boolean hasImage = document.getBoolean("hasImage");
        String imageBase64 = document.getString("imageBase64");

        return emergencyFromSnapshot(
                document.getId(),
                document.getString("municipality"),
                lat,
                lng,
                document.getString("phoneNumber"),
                document.getString("reportedBy"),
                document.getString("reportedByProfileImageBase64"),
                document.getString("status"),
                hasImage,
                imageBase64,
                getTimestampMillis(document));
    }

    private AdminEmergency emergencyFromSnapshot(String id,
                                                 String municipality,
                                                 Double latitude,
                                                 Double longitude,
                                                 String phoneNumber,
                                                 String reportedBy,
                                                 String reportedByProfileImageBase64,
                                                 String status,
                                                 Boolean hasImage,
                                                 String imageBase64,
                                                 long timestamp) {
        return new AdminEmergency(
                id,
                municipality,
                latitude == null ? 0 : latitude,
                longitude == null ? 0 : longitude,
                phoneNumber,
                reportedBy,
                reportedByProfileImageBase64 == null ? "" : reportedByProfileImageBase64,
                status,
                hasImage != null && hasImage,
                imageBase64 == null ? "" : imageBase64,
                timestamp);
    }

    private long getTimestampMillis(QueryDocumentSnapshot document) {
        return getTimestampMillis(document.get("timestamp"));
    }

    private long getTimestampMillis(Object timestampObj) {
        if (timestampObj instanceof Long) {
            return (Long) timestampObj;
        }
        if (timestampObj instanceof Timestamp) {
            return ((Timestamp) timestampObj).toDate().getTime();
        }
        return 0;
    }
}
