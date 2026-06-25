package com.appname.dqra.model;

public class UserProfile {

    private final String id;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String address;
    private final String role;
    private final String profileImageBase64;

    public UserProfile(String id, String fullName, String email, String phone, String address, String role,
                       String profileImageBase64) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.role = role;
        this.profileImageBase64 = profileImageBase64;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getRole() {
        return role;
    }

    public String getProfileImageBase64() {
        return profileImageBase64;
    }

    public boolean isComplete() {
        return hasText(fullName) && hasText(phone) && hasText(address);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
