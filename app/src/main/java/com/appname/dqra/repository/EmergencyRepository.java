package com.appname.dqra.repository;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.model.EmergencyLocation;
import com.appname.dqra.security.AesCrypto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmergencyRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    public interface ListenerHandle {
        void remove();
    }

    public interface EmergencyStatusListener {
        void onStatusChanged(String status);
        void onDeleted();
        void onError(Exception exception);
    }

    public interface LatestEmergencyListener {
        void onEmergency(String emergencyId, String municipality, long timestamp);
        void onError(Exception exception);
    }

    public static class CooldownState {
        private final long remainingMs;
        private final long cooldownMs;

        public CooldownState(long remainingMs, long cooldownMs) {
            this.remainingMs = remainingMs;
            this.cooldownMs = cooldownMs;
        }

        public boolean isOnCooldown() {
            return remainingMs > 0;
        }

        public long getRemainingMs() {
            return remainingMs;
        }

        public long getCooldownMs() {
            return cooldownMs;
        }
    }

    private static final String ADMINS_COLLECTION = "admins_with_stations";
    private static final String RESCUERS_COLLECTION = "rescuers";
    private static final String EMERGENCIES_COLLECTION = "emergency_locations";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String STATUS_COMPLETED = "completed";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public EmergencyRepository() {
        this(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    EmergencyRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this.auth = auth;
        this.firestore = firestore;
    }

    public void fetchCurrentUserMunicipality(Callback<String> callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        String cachedMunicipality = AppCache.getInstance().getUserMunicipality(userId);
        boolean hasCache = cachedMunicipality != null && !cachedMunicipality.isEmpty();
        if (hasCache) {
            callback.onSuccess(cachedMunicipality);
        }

        firestore.collection(ADMINS_COLLECTION).document(userId)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc.exists()) {
                        String municipality = adminDoc.getString("municipality");
                        if (municipality == null || municipality.isEmpty()) {
                            if (!hasCache) {
                                callback.onError(new IllegalStateException("Municipality not set for admin"));
                            }
                        } else {
                            AppCache.getInstance().putUserMunicipality(userId, municipality);
                            AppCache.getInstance().putAdminMunicipality(userId, municipality);
                            callback.onSuccess(municipality);
                        }
                        return;
                    }

                    firestore.collection(RESCUERS_COLLECTION).document(userId)
                            .get()
                            .addOnSuccessListener(rescuerDoc -> {
                                if (!rescuerDoc.exists()) {
                                    if (!hasCache) {
                                        callback.onError(new IllegalStateException("User not found"));
                                    }
                                    return;
                                }

                                String municipality = rescuerDoc.getString("municipality");
                                if (municipality == null || municipality.isEmpty()) {
                                    if (!hasCache) {
                                        callback.onError(new IllegalStateException("Municipality not set for rescuer"));
                                    }
                                } else {
                                    AppCache.getInstance().putUserMunicipality(userId, municipality);
                                    callback.onSuccess(municipality);
                                }
                            })
                            .addOnFailureListener(exception -> {
                                if (!hasCache) {
                                    callback.onError(exception);
                                }
                            });
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    public void fetchCurrentAdminMunicipality(Callback<String> callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new IllegalStateException("User is not signed in"));
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        String cachedMunicipality = AppCache.getInstance().getAdminMunicipality(userId);
        boolean hasCache = cachedMunicipality != null && !cachedMunicipality.isEmpty();
        if (hasCache) {
            callback.onSuccess(cachedMunicipality);
        }

        firestore.collection(ADMINS_COLLECTION).document(userId)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (!adminDoc.exists()) {
                        if (!hasCache) {
                            callback.onError(new IllegalStateException("Admin account not found"));
                        }
                        return;
                    }

                    String municipality = adminDoc.getString("municipality");
                    if (municipality == null || municipality.isEmpty()) {
                        if (!hasCache) {
                            callback.onError(new IllegalStateException("Municipality not set for admin"));
                        }
                    } else {
                        AppCache.getInstance().putAdminMunicipality(userId, municipality);
                        callback.onSuccess(municipality);
                    }
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    public void fetchEmergencyLogs(String municipality, String statusFilter,
                                   Callback<List<EmergencyLocation>> callback) {
        fetchEmergencyLogs(municipality, statusFilter, false, callback);
    }

    public void fetchEmergencyLogs(String municipality, String statusFilter, boolean onlyCurrentAdminHandled,
                                   Callback<List<EmergencyLocation>> callback) {
        List<EmergencyLocation> cachedLogs = AppCache.getInstance()
                .getEmergencyLogs(municipality, statusFilter);
        boolean hasCache = !onlyCurrentAdminHandled && cachedLogs != null && !cachedLogs.isEmpty();
        if (hasCache) {
            callback.onSuccess(cachedLogs);
        }

        String currentAdminId = auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
        long startOfToday = startOfTodayMillis();

        Query query = firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("municipality", municipality);

        if (statusFilter != null) {
            query = query.whereEqualTo("status", statusFilter);
        }

        query.get()
                .addOnSuccessListener(snapshot -> {
                    List<EmergencyLocation> emergencies = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        if (onlyCurrentAdminHandled
                                && (!isHandledByCurrentAdmin(document, currentAdminId)
                                || getAdminStatusTimestamp(document, statusFilter) < startOfToday)) {
                            continue;
                        }
                        EmergencyLocation emergency = document.toObject(EmergencyLocation.class);
                        emergency.setId(document.getId());
                        emergencies.add(emergency);
                    }

                    Collections.sort(emergencies, (first, second) ->
                            Long.compare(second.getTimestamp(), first.getTimestamp()));
                    if (!onlyCurrentAdminHandled) {
                        AppCache.getInstance().putEmergencyLogs(municipality, statusFilter, emergencies);
                    }
                    callback.onSuccess(emergencies);
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }

    public void fetchAdminStationName(String adminId, Callback<String> callback) {
        if (adminId == null || adminId.trim().isEmpty()) {
            callback.onSuccess("Unknown station");
            return;
        }

        firestore.collection(ADMINS_COLLECTION)
                .document(adminId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onSuccess("Unknown station");
                        return;
                    }

                    String stationName = AesCrypto.decrypt(document.getString("stationName"));
                    if (stationName == null || stationName.trim().isEmpty()) {
                        callback.onSuccess("Unknown station");
                        return;
                    }

                    callback.onSuccess(stationName);
                })
                .addOnFailureListener(callback::onError);
    }

    public String getCurrentAdminId() {
        return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid();
    }

    public void hasPendingEmergency(String reportedBy, Callback<Boolean> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("reportedBy", reportedBy)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(!snapshot.isEmpty()))
                .addOnFailureListener(callback::onError);
    }

    public void hasActiveEmergencyForUser(String userId, Callback<Boolean> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("reportedByUid", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean hasActiveEmergency = false;
                    for (QueryDocumentSnapshot document : snapshot) {
                        String status = document.getString("status");
                        if ("pending".equals(status) || "confirmed".equals(status)) {
                            hasActiveEmergency = true;
                            break;
                        }
                    }
                    callback.onSuccess(hasActiveEmergency);
                })
                .addOnFailureListener(callback::onError);
    }

    public void getResolvedEmergencyCooldown(String userId, long baseCooldownMs,
                                             long maxCooldownMs,
                                             Callback<CooldownState> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("reportedByUid", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int resolvedCount = 0;
                    long latestResolvedAt = 0L;

                    for (QueryDocumentSnapshot document : snapshot) {
                        String status = document.getString("status");
                        if (!STATUS_RESOLVED.equals(status) && !STATUS_COMPLETED.equals(status)) {
                            continue;
                        }

                        resolvedCount++;
                        long resolvedAt = getStatusResolvedTimestamp(document);
                        if (resolvedAt > latestResolvedAt) {
                            latestResolvedAt = resolvedAt;
                        }
                    }

                    if (resolvedCount == 0 || latestResolvedAt <= 0) {
                        callback.onSuccess(new CooldownState(0L, 0L));
                        return;
                    }

                    long multiplier = 1L << Math.min(resolvedCount - 1, 10);
                    long cooldownMs = Math.min(baseCooldownMs * multiplier, maxCooldownMs);
                    long remainingMs = Math.max(0L,
                            latestResolvedAt + cooldownMs - System.currentTimeMillis());
                    callback.onSuccess(new CooldownState(remainingMs, cooldownMs));
                })
                .addOnFailureListener(callback::onError);
    }

    public void createEmergencyRequest(String adminId, String municipality,
                                       double latitude, double longitude,
                                       String fullName, String phoneNumber, String uid,
                                       String profileImageBase64,
                                       Callback<String> callback) {
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("municipality", municipality);
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("reportedBy", fullName);
        locationData.put("reportedByUid", uid);
        locationData.put("phoneNumber", phoneNumber);
        locationData.put("reportedByProfileImageBase64", profileImageBase64 == null ? "" : profileImageBase64);
        locationData.put("status", "pending");
        locationData.put("adminId", adminId);
        locationData.put("hasImage", false);

        firestore.collection(EMERGENCIES_COLLECTION)
                .add(locationData)
                .addOnSuccessListener(documentReference -> callback.onSuccess(documentReference.getId()))
                .addOnFailureListener(callback::onError);
    }

    public ListenerHandle listenToEmergencyStatus(String emergencyId, EmergencyStatusListener listener) {
        ListenerRegistration registration = firestore.collection(EMERGENCIES_COLLECTION)
                .document(emergencyId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        listener.onDeleted();
                        return;
                    }

                    listener.onStatusChanged(snapshot.getString("status"));
                });
        return registration::remove;
    }

    public ListenerHandle listenForLatestEmergency(LatestEmergencyListener listener) {
        ListenerRegistration registration = firestore.collection(EMERGENCIES_COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        return;
                    }

                    for (QueryDocumentSnapshot document : snapshots) {
                        String municipality = document.getString("municipality");
                        long timestamp = getTimestampMillis(document);
                        listener.onEmergency(document.getId(), municipality, timestamp);
                    }
                });
        return registration::remove;
    }

    public void cancelEmergency(String emergencyId, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "cancelled");
        updates.put("cancelledAt", System.currentTimeMillis());
        updates.put("cancelledBy", auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid() : "unknown");

        firestore.collection(EMERGENCIES_COLLECTION)
                .document(emergencyId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void updateEmergencyImage(String emergencyId, String imageBase64, int imageSize,
                                     Callback<Void> callback) {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("imageBase64", imageBase64);
        updateData.put("hasImage", true);
        updateData.put("imageTimestamp", System.currentTimeMillis());
        updateData.put("imageSize", imageSize);

        firestore.collection(EMERGENCIES_COLLECTION)
                .document(emergencyId)
                .update(updateData)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void resolveEmergency(EmergencyLocation emergency, Callback<Void> callback) {
        String docId = emergency.getId();
        if (docId == null || docId.isEmpty()) {
            resolveEmergencyByQuery(emergency, callback);
            return;
        }

        updateEmergencyResolved(docId, callback);
    }

    public void fetchPhoneNumber(String reportedBy, Callback<String> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("reportedBy", reportedBy)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }
                    callback.onSuccess(snapshot.getDocuments().get(0).getString("phoneNumber"));
                })
                .addOnFailureListener(callback::onError);
    }

    private void resolveEmergencyByQuery(EmergencyLocation emergency, Callback<Void> callback) {
        firestore.collection(EMERGENCIES_COLLECTION)
                .whereEqualTo("reportedBy", emergency.getReportedBy())
                .whereEqualTo("timestamp", emergency.getTimestamp())
                .whereEqualTo("municipality", emergency.getMunicipality())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onError(new IllegalStateException("Emergency not found in database"));
                        return;
                    }
                    updateEmergencyResolved(snapshot.getDocuments().get(0).getId(), callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void updateEmergencyResolved(String docId, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", STATUS_RESOLVED);
        updates.put("resolvedAt", FieldValue.serverTimestamp());
        updates.put("resolvedBy", auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid() : "unknown");

        firestore.collection(EMERGENCIES_COLLECTION)
                .document(docId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private long getTimestampMillis(DocumentSnapshot document) {
        Object timestamp = document.get("timestamp");
        if (timestamp instanceof Number) {
            return ((Number) timestamp).longValue();
        }
        if (timestamp instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) timestamp).toDate().getTime();
        }
        return 0;
    }

    private long getStatusResolvedTimestamp(DocumentSnapshot document) {
        long resolvedAt = getTimestampMillisValue(document.get("resolvedAt"));
        return resolvedAt > 0 ? resolvedAt : getTimestampMillis(document);
    }

    private boolean isHandledByCurrentAdmin(DocumentSnapshot document, String adminId) {
        return adminId != null && (adminId.equals(document.getString("confirmedBy"))
                || adminId.equals(document.getString("resolvedBy")));
    }

    private long getAdminStatusTimestamp(DocumentSnapshot document, String status) {
        if ("confirmed".equals(status)) {
            long confirmedAt = getTimestampMillisValue(document.get("confirmedAt"));
            return confirmedAt > 0 ? confirmedAt : getTimestampMillis(document);
        }

        long resolvedAt = getTimestampMillisValue(document.get("resolvedAt"));
        return resolvedAt > 0 ? resolvedAt : getTimestampMillis(document);
    }

    private long getTimestampMillisValue(Object timestamp) {
        if (timestamp instanceof Number) {
            return ((Number) timestamp).longValue();
        }
        if (timestamp instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) timestamp).toDate().getTime();
        }
        return 0;
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
