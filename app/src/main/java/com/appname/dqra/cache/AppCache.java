package com.appname.dqra.cache;

import android.content.Context;
import android.content.SharedPreferences;

import com.appname.dqra.model.AdminProfile;
import com.appname.dqra.model.EmergencyContact;
import com.appname.dqra.model.EmergencyLocation;
import com.appname.dqra.model.RescueStation;
import com.appname.dqra.model.UserProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppCache {

    private static final String PREFS_NAME = "dqra_repository_cache";
    private static final String KEY_RESCUE_STATIONS = "rescue_stations";
    private static final String KEY_EMERGENCY_CONTACTS = "emergency_contacts";

    private static AppCache instance;

    private final SharedPreferences preferences;

    private AppCache(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void initialize(Context context) {
        if (instance == null) {
            instance = new AppCache(context);
        }
    }

    public static AppCache getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AppCache must be initialized by DqraApplication");
        }
        return instance;
    }

    public void putUserProfile(String userId, UserProfile profile) {
        putObject("user_profile_" + userId, userProfileToJson(profile));
    }

    public UserProfile getUserProfile(String userId) {
        JSONObject object = getObject("user_profile_" + userId);
        return object == null ? null : userProfileFromJson(object);
    }

    public void putAdminProfile(String userId, AdminProfile profile) {
        putObject("admin_profile_" + userId, adminProfileToJson(profile));
    }

    public AdminProfile getAdminProfile(String userId) {
        JSONObject object = getObject("admin_profile_" + userId);
        return object == null ? null : adminProfileFromJson(object);
    }

    public void putAdminMunicipality(String userId, String municipality) {
        preferences.edit().putString("admin_municipality_" + userId, municipality).apply();
    }

    public String getAdminMunicipality(String userId) {
        return preferences.getString("admin_municipality_" + userId, null);
    }

    public void putUserMunicipality(String userId, String municipality) {
        preferences.edit().putString("user_municipality_" + userId, municipality).apply();
    }

    public String getUserMunicipality(String userId) {
        return preferences.getString("user_municipality_" + userId, null);
    }

    public void putRescueStations(List<RescueStation> stations) {
        putArray(KEY_RESCUE_STATIONS, rescueStationsToJson(stations));
    }

    public List<RescueStation> getRescueStations() {
        JSONArray array = getArray(KEY_RESCUE_STATIONS);
        return array == null ? null : rescueStationsFromJson(array);
    }

    public void putEmergencyContacts(List<EmergencyContact> contacts) {
        putArray(KEY_EMERGENCY_CONTACTS, emergencyContactsToJson(contacts));
    }

    public List<EmergencyContact> getEmergencyContacts() {
        JSONArray array = getArray(KEY_EMERGENCY_CONTACTS);
        return array == null ? null : emergencyContactsFromJson(array);
    }

    public void putEmergencyLogs(String municipality, String statusFilter, List<EmergencyLocation> logs) {
        putArray(emergencyLogsKey(municipality, statusFilter), emergencyLogsToJson(logs));
    }

    public List<EmergencyLocation> getEmergencyLogs(String municipality, String statusFilter) {
        JSONArray array = getArray(emergencyLogsKey(municipality, statusFilter));
        return array == null ? null : emergencyLogsFromJson(array);
    }

    public void addRejectedEmergencyForAdmin(String adminId, String emergencyId) {
        if (adminId == null || adminId.isEmpty() || emergencyId == null || emergencyId.isEmpty()) {
            return;
        }

        String key = rejectedEmergenciesKey(adminId);
        Set<String> rejectedIds = new HashSet<>(preferences.getStringSet(key, new HashSet<>()));
        rejectedIds.add(emergencyId);
        preferences.edit().putStringSet(key, rejectedIds).apply();
    }

    public boolean isEmergencyRejectedByAdmin(String adminId, String emergencyId) {
        if (adminId == null || adminId.isEmpty() || emergencyId == null || emergencyId.isEmpty()) {
            return false;
        }

        return preferences.getStringSet(rejectedEmergenciesKey(adminId), new HashSet<>())
                .contains(emergencyId);
    }

    private void putObject(String key, JSONObject object) {
        preferences.edit().putString(key, object.toString()).apply();
    }

    private JSONObject getObject(String key) {
        String value = preferences.getString(key, null);
        if (value == null) {
            return null;
        }
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            preferences.edit().remove(key).apply();
            return null;
        }
    }

    private void putArray(String key, JSONArray array) {
        preferences.edit().putString(key, array.toString()).apply();
    }

    private JSONArray getArray(String key) {
        String value = preferences.getString(key, null);
        if (value == null) {
            return null;
        }
        try {
            return new JSONArray(value);
        } catch (JSONException e) {
            preferences.edit().remove(key).apply();
            return null;
        }
    }

    private String emergencyLogsKey(String municipality, String statusFilter) {
        return "emergency_logs_" + safe(municipality) + "_" + safe(statusFilter);
    }

    private String rejectedEmergenciesKey(String adminId) {
        return "rejected_emergencies_" + safe(adminId);
    }

    private JSONObject userProfileToJson(UserProfile profile) {
        JSONObject object = new JSONObject();
        put(object, "id", profile.getId());
        put(object, "fullName", profile.getFullName());
        put(object, "email", profile.getEmail());
        put(object, "phone", profile.getPhone());
        put(object, "address", profile.getAddress());
        put(object, "role", profile.getRole());
        return object;
    }

    private UserProfile userProfileFromJson(JSONObject object) {
        return new UserProfile(
                nullableString(object, "id"),
                nullableString(object, "fullName"),
                nullableString(object, "email"),
                nullableString(object, "phone"),
                nullableString(object, "address"),
                nullableString(object, "role"),
                null);
    }

    private JSONObject adminProfileToJson(AdminProfile profile) {
        JSONObject object = new JSONObject();
        put(object, "stationName", profile.getStationName());
        put(object, "municipality", profile.getMunicipality());
        put(object, "stationPhone", profile.getStationPhone());
        put(object, "email", profile.getEmail());
        return object;
    }

    private AdminProfile adminProfileFromJson(JSONObject object) {
        return new AdminProfile(
                nullableString(object, "stationName"),
                nullableString(object, "municipality"),
                nullableString(object, "stationPhone"),
                nullableString(object, "email"),
                null);
    }

    private JSONArray rescueStationsToJson(List<RescueStation> stations) {
        JSONArray array = new JSONArray();
        for (RescueStation station : stations) {
            JSONObject object = new JSONObject();
            put(object, "id", station.getId());
            put(object, "name", station.getName());
            put(object, "stationName", station.getStationName());
            put(object, "municipality", station.getMunicipality());
            put(object, "phoneNumber", station.getPhoneNumber());
            put(object, "phoneNumber2", station.getPhoneNumber2());
            put(object, "messengerLink", station.getMessengerLink());
            put(object, "profileImageBase64", station.getProfileImageBase64());
            put(object, "latitude", station.getLatitude());
            put(object, "longitude", station.getLongitude());
            array.put(object);
        }
        return array;
    }

    private List<RescueStation> rescueStationsFromJson(JSONArray array) {
        List<RescueStation> stations = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            RescueStation station = new RescueStation();
            station.setId(nullableString(object, "id"));
            station.setName(nullableString(object, "name"));
            station.setStationName(nullableString(object, "stationName"));
            station.setMunicipality(nullableString(object, "municipality"));
            station.setPhoneNumber(nullableString(object, "phoneNumber"));
            station.setPhoneNumber2(nullableString(object, "phoneNumber2"));
            station.setMessengerLink(nullableString(object, "messengerLink"));
            station.setProfileImageBase64(nullableString(object, "profileImageBase64"));
            station.setLatitude(object.optDouble("latitude", 0));
            station.setLongitude(object.optDouble("longitude", 0));
            stations.add(station);
        }
        return stations;
    }

    private JSONArray emergencyContactsToJson(List<EmergencyContact> contacts) {
        JSONArray array = new JSONArray();
        for (EmergencyContact contact : contacts) {
            JSONObject object = new JSONObject();
            put(object, "id", contact.getId());
            put(object, "barangay", contact.getBarangay());
            put(object, "stationName", contact.getStationName());
            put(object, "phoneNumber", contact.getPhoneNumber());
            array.put(object);
        }
        return array;
    }

    private List<EmergencyContact> emergencyContactsFromJson(JSONArray array) {
        List<EmergencyContact> contacts = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            EmergencyContact contact = new EmergencyContact();
            contact.setId(nullableString(object, "id"));
            contact.setBarangay(nullableString(object, "barangay"));
            contact.setStationName(nullableString(object, "stationName"));
            contact.setPhoneNumber(nullableString(object, "phoneNumber"));
            contacts.add(contact);
        }
        return contacts;
    }

    private JSONArray emergencyLogsToJson(List<EmergencyLocation> logs) {
        JSONArray array = new JSONArray();
        for (EmergencyLocation log : logs) {
            JSONObject object = new JSONObject();
            put(object, "id", log.getId());
            put(object, "municipality", log.getMunicipality());
            put(object, "latitude", log.getLatitude());
            put(object, "longitude", log.getLongitude());
            put(object, "reportedBy", log.getReportedBy());
            put(object, "timestamp", log.getTimestamp());
            put(object, "status", log.getStatus());
            put(object, "confirmedBy", log.getConfirmedBy());
            put(object, "resolvedBy", log.getResolvedBy());
            array.put(object);
        }
        return array;
    }

    private List<EmergencyLocation> emergencyLogsFromJson(JSONArray array) {
        List<EmergencyLocation> logs = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            EmergencyLocation log = new EmergencyLocation();
            log.setId(nullableString(object, "id"));
            log.setMunicipality(nullableString(object, "municipality"));
            log.setLatitude(object.optDouble("latitude", 0));
            log.setLongitude(object.optDouble("longitude", 0));
            log.setReportedBy(nullableString(object, "reportedBy"));
            log.setTimestamp(object.optLong("timestamp", 0));
            log.setStatus(nullableString(object, "status"));
            log.setConfirmedBy(nullableString(object, "confirmedBy"));
            log.setResolvedBy(nullableString(object, "resolvedBy"));
            logs.add(log);
        }
        return logs;
    }

    private void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value == null ? JSONObject.NULL : value);
        } catch (JSONException ignored) {
        }
    }

    private String safe(String value) {
        return value == null ? "all" : value.trim().toLowerCase();
    }

    private String nullableString(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }
}
