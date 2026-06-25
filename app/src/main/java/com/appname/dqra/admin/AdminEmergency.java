package com.appname.dqra.admin;

public class AdminEmergency {

    private final String id;
    private final String municipality;
    private final double latitude;
    private final double longitude;
    private final String phoneNumber;
    private final String reportedBy;
    private final String status;
    private final boolean hasImage;
    private final String imageBase64;
    private final long timestamp;

    public AdminEmergency(String id, String municipality, double latitude, double longitude,
                          String phoneNumber, String reportedBy, String status,
                          boolean hasImage, String imageBase64, long timestamp) {
        this.id = id;
        this.municipality = municipality;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phoneNumber = phoneNumber;
        this.reportedBy = reportedBy;
        this.status = status;
        this.hasImage = hasImage;
        this.imageBase64 = imageBase64;
        this.timestamp = timestamp;
    }

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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public String getStatus() {
        return status;
    }

    public boolean hasImage() {
        return hasImage;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
