package com.appname.dqra.model;

public class AdminProfile {
    private final String stationName;
    private final String municipality;
    private final String stationPhone;
    private final String email;
    private final String profileImageBase64;

    public AdminProfile(String stationName, String municipality, String stationPhone,
                        String email, String profileImageBase64) {
        this.stationName = stationName;
        this.municipality = municipality;
        this.stationPhone = stationPhone;
        this.email = email;
        this.profileImageBase64 = profileImageBase64;
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
}
