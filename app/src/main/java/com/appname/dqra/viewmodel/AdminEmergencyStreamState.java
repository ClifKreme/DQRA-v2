package com.appname.dqra.viewmodel;

public class AdminEmergencyStreamState {

    public enum Status {
        IDLE,
        EMPTY,
        EMERGENCY,
        ERROR
    }

    private final Status status;
    private final AdminEmergency emergency;
    private final String message;

    private AdminEmergencyStreamState(Status status, AdminEmergency emergency, String message) {
        this.status = status;
        this.emergency = emergency;
        this.message = message;
    }

    public static AdminEmergencyStreamState idle() {
        return new AdminEmergencyStreamState(Status.IDLE, null, null);
    }

    public static AdminEmergencyStreamState empty() {
        return new AdminEmergencyStreamState(Status.EMPTY, null, null);
    }

    public static AdminEmergencyStreamState emergency(AdminEmergency emergency) {
        return new AdminEmergencyStreamState(Status.EMERGENCY, emergency, null);
    }

    public static AdminEmergencyStreamState error(String message) {
        return new AdminEmergencyStreamState(Status.ERROR, null, message);
    }

    public Status getStatus() {
        return status;
    }

    public AdminEmergency getEmergency() {
        return emergency;
    }

    public String getMessage() {
        return message;
    }
}
