package com.appname.dqra.repository;

import com.appname.dqra.cache.AppCache;
import com.appname.dqra.model.EmergencyContact;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmergencyContactRepository {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception exception);
    }

    private static final String CONTACTS_COLLECTION = "emergency_contacts";

    private final FirebaseFirestore firestore;

    public EmergencyContactRepository() {
        this(FirebaseFirestore.getInstance());
    }

    EmergencyContactRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public void addContact(String barangay, String stationName, String phone, Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("barangay", barangay);
        data.put("stationName", stationName);
        data.put("phoneNumber", phone);

        firestore.collection(CONTACTS_COLLECTION)
                .add(data)
                .addOnSuccessListener(ref -> {
                    List<EmergencyContact> cachedContacts = AppCache.getInstance().getEmergencyContacts();
                    if (cachedContacts != null) {
                        EmergencyContact contact = new EmergencyContact(barangay, stationName, phone);
                        contact.setId(ref.getId());
                        cachedContacts.add(contact);
                        AppCache.getInstance().putEmergencyContacts(cachedContacts);
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchContacts(Callback<List<EmergencyContact>> callback) {
        List<EmergencyContact> cachedContacts = AppCache.getInstance().getEmergencyContacts();
        boolean hasCache = cachedContacts != null && !cachedContacts.isEmpty();
        if (hasCache) {
            callback.onSuccess(cachedContacts);
        }

        firestore.collection(CONTACTS_COLLECTION)
                .orderBy("barangay")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<EmergencyContact> contacts = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        EmergencyContact contact = document.toObject(EmergencyContact.class);
                        if (contact != null) {
                            contact.setId(document.getId());
                            contacts.add(contact);
                        }
                    });
                    AppCache.getInstance().putEmergencyContacts(contacts);
                    callback.onSuccess(contacts);
                })
                .addOnFailureListener(exception -> {
                    if (!hasCache) {
                        callback.onError(exception);
                    }
                });
    }
}
