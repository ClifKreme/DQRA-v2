package com.appname.dqra.model;

public class AdminProfile {
    private final String stationName;
    private final String municipality;
    private final String stationPhone;
    private final String email;
    private final String profileImageBase64;
    private final long profileLastUpdatedAt;
    private final long photoLastUpdatedAt;

    public AdminProfile(String stationName, String municipality, String stationPhone,
                        String email, String profileImageBase64) {
        this(stationName, municipality, stationPhone, email, profileImageBase64, 0L, 0L);
    }

    public AdminProfile(String stationName, String municipality, String stationPhone,
                        String email, String profileImageBase64, long profileLastUpdatedAt) {
        this(stationName, municipality, stationPhone, email, profileImageBase64, profileLastUpdatedAt, 0L);
    }

    public AdminProfile(String stationName, String municipality, String stationPhone,
                        String email, String profileImageBase64, long profileLastUpdatedAt,
                        long photoLastUpdatedAt) {
        this.stationName = stationName;
        this.municipality = municipality;
        this.stationPhone = stationPhone;
        this.email = email;
        this.profileImageBase64 = profileImageBase64;
        this.profileLastUpdatedAt = profileLastUpdatedAt;
        this.photoLastUpdatedAt = photoLastUpdatedAt;
    }

    public String getStationName() {
        return stationName;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getStationPhone() {
        return stationPhone;
    }

    public String getEmail() {
        return email;
    }

    public String getProfileImageBase64() {
        return profileImageBase64;
    }

    /** Epoch-millis timestamp of the last profile text update, or 0 if never updated. */
    public long getProfileLastUpdatedAt() {
        return profileLastUpdatedAt;
    }

    /** Epoch-millis timestamp of the last profile photo update, or 0 if never changed. */
    public long getPhotoLastUpdatedAt() {
        return photoLastUpdatedAt;
    }
}
