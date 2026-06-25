package com.appname.dqra.model;

public class Rescuer {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String municipality;
    private String token;

    // No-argument constructor required for Firestore
    public Rescuer() {
        // Required for Firestore
    }

    // Constructor with parameters
    public Rescuer(String id, String name, String email, String phone, String municipality, String token) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.municipality = municipality;
        this.token = token;

    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getToken() {
        return token;
    }


}