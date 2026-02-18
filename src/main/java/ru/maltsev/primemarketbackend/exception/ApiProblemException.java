package ru.maltsev.primemarketbackend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class ApiProblemException extends ResponseStatusException {
    private final String code;

    public ApiProblemException(HttpStatusCode status, String code, String reason) {
        super(status, reason);
        this.code = code;
    }

}
