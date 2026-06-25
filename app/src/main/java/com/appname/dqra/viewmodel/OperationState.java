package com.appname.dqra.viewmodel;

public class OperationState {

    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final Status status;
    private final String message;

    private OperationState(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static OperationState idle() {
        return new OperationState(Status.IDLE, null);
    }

    public static OperationState loading() {
        return new OperationState(Status.LOADING, null);
    }

    public static OperationState success(String message) {
        return new OperationState(Status.SUCCESS, message);
    }

    public static OperationState error(String message) {
        return new OperationState(Status.ERROR, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
