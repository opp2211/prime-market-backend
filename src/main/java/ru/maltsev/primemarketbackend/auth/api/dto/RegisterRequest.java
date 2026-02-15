package ru.maltsev.primemarketbackend.auth.api.dto;

public record RegisterRequest(String username, String email, String password) {
}
