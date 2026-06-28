package com.appname.dqra.model;

public class AppFeedback {
    private String id;
    private String senderUid;
    private String senderName;
    private String senderRole;
    private String municipality;
    private String message;
    private int rating;
    private long timestamp;
    private long weekStart;

    public AppFeedback() {
    }

    public AppFeedback(String senderUid, String senderName, String senderRole,
                       String municipality, String message, int rating,
                       long timestamp, long weekStart) {
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.municipality = municipality;
        this.message = message;
        this.rating = rating;
        this.timestamp = timestamp;
        this.weekStart = weekStart;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getMessage() {
        return message;
    }

    public int getRating() {
        return rating;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getWeekStart() {
        return weekStart;
    }
}
