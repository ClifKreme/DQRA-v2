package com.appname.dqra.model;

/** Firestore document stored in the "emergency_contacts" collection. */
public class EmergencyContact {

    private String id;           // Firestore document ID
    private String barangay;     // Municipality / Barangay name
    private String stationName;  // Name of the rescue station
    private String phoneNumber;  // Primary contact number

    // Required empty constructor for Firestore deserialization
    public EmergencyContact() {}

    public EmergencyContact(String barangay, String stationName, String phoneNumber) {
        this.barangay    = barangay;
        this.stationName = stationName;
        this.phoneNumber = phoneNumber;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getId()                    { return id; }
    public void   setId(String id)           { this.id = id; }

    public String getBarangay()              { return barangay; }
    public void   setBarangay(String v)      { this.barangay = v; }

    public String getStationName()           { return stationName; }
    public void   setStationName(String v)   { this.stationName = v; }

    public String getPhoneNumber()           { return phoneNumber; }
    public void   setPhoneNumber(String v)   { this.phoneNumber = v; }
}
