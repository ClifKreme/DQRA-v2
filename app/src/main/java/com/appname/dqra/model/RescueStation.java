package com.appname.dqra.model;

public class RescueStation {

    private String id;
    private String name;
    private String stationName;
    private String municipality;
    private String phoneNumber;
    private String phoneNumber2;   // ← NEW: second phone number
    private String messengerLink;
    private double latitude;
    private double longitude;

    // ─── Constructors ────────────────────────────────────────────────────────

    public RescueStation() {}

    public RescueStation(String name, String municipality, String phoneNumber) {
        this.name = name;
        this.municipality = municipality;
        this.phoneNumber = phoneNumber;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    // ── Phone Number 2 ──
    public String getPhoneNumber2() { return phoneNumber2; }
    public void setPhoneNumber2(String phoneNumber2) { this.phoneNumber2 = phoneNumber2; }

    public String getMessengerLink() { return messengerLink; }
    public void setMessengerLink(String messengerLink) { this.messengerLink = messengerLink; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
}