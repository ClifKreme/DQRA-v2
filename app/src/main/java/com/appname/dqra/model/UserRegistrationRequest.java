package com.appname.dqra.model;

public class UserRegistrationRequest {
    private final String id;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String address;
    private final String selfieImageBase64;
    private final String nationalIdFrontBase64;
    private final String nationalIdBackBase64;
    private final String status;
    private final long submittedAt;

    public UserRegistrationRequest(String id, String fullName, String email, String phone,
                                   String address, String selfieImageBase64,
                                   String nationalIdFrontBase64, String nationalIdBackBase64,
                                   String status, long submittedAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.selfieImageBase64 = selfieImageBase64;
        this.nationalIdFrontBase64 = nationalIdFrontBase64;
        this.nationalIdBackBase64 = nationalIdBackBase64;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getSelfieImageBase64() { return selfieImageBase64; }
    public String getNationalIdFrontBase64() { return nationalIdFrontBase64; }
    public String getNationalIdBackBase64() { return nationalIdBackBase64; }

    /** @deprecated Use {@link #getNationalIdFrontBase64()} and {@link #getNationalIdBackBase64()} */
    @Deprecated
    public String getNationalIdImageBase64() { return nationalIdFrontBase64; }

    public String getStatus() { return status; }
    public long getSubmittedAt() { return submittedAt; }
}
