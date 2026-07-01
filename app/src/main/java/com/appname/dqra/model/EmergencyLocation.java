package com.appname.dqra.model;

public class EmergencyLocation {
    private String id; // NEW: Document ID
    private String municipality;
    private double latitude;
    private double longitude;
    private String reportedBy;
    private long timestamp;
    private String status; // NEW: Status field (pending, confirmed, resolved, rejected)
    private String confirmedBy;
    private String resolvedBy;

    // Empty constructor needed for Firestore
    public EmergencyLocation() {
    }

    // Constructor with all fields
    public EmergencyLocation(String municipality, double latitude, double longitude,
                             String reportedBy, long timestamp, String status) {
        this.municipality = municipality;
        this.latitude = latitude;
        this.longitude = longitude;
        this.reportedBy = reportedBy;
        this.timestamp = timestamp;
        this.status = status;
    }

    // Constructor without status (for backward compatibility)
    public EmergencyLocation(String municipality, double latitude, double longitude,
                             String reportedBy, long timestamp) {
        this.municipality = municipality;
        this.latitude = latitude;
        this.longitude = longitude;
        this.reportedBy = reportedBy;
        this.timestamp = timestamp;
        this.status = "pending"; // Default status
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getMunicipality() {
        return municipality;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    // Setters (needed for Firestore)
    public void setId(String id) {
        this.id = id;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }
}
