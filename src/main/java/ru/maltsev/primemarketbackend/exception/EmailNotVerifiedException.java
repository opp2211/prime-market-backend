package ru.maltsev.primemarketbackend.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email not verified");
    }
}
