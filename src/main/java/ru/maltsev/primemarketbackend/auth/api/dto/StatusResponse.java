package ru.maltsev.primemarketbackend.auth.api.dto;

public record StatusResponse(String status) {
    public static StatusResponse emailVerificationRequired() {
        return new StatusResponse("EMAIL_VERIFICATION_REQUIRED");
    }

    public static StatusResponse registered() {
        return new StatusResponse("REGISTERED");
    }

    public static StatusResponse sent() {
        return new StatusResponse("SENT");
    }
}
