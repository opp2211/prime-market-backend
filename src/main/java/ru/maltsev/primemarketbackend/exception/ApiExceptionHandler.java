package ru.maltsev.primemarketbackend.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String detail = ex.getReason();
        if (detail == null || detail.isBlank()) {
            detail = "Unexpected error";
        }
        log.warn("Handled ResponseStatusException: status={}, detail={}", status, detail, ex);
        String code = ex instanceof ApiProblemException apiProblem ? apiProblem.getCode() : toCode(status);
        ProblemDetail problem = buildProblem(status, code, detail, toTitle(status));
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ProblemDetail> handleEmailNotVerified(EmailNotVerifiedException ex) {
        log.warn("Handled EmailNotVerifiedException", ex);
        ProblemDetail problem = buildProblem(
            HttpStatus.CONFLICT,
            "EMAIL_NOT_VERIFIED",
            ex.getMessage(),
            "Email not verified"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Handled AuthenticationException", ex);
        ProblemDetail problem = buildProblem(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "Invalid credentials",
            "Invalid credentials"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Handled MethodArgumentNotValidException", ex);
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("field", fieldError.getField());
            entry.put("message", fieldError.getDefaultMessage());
            errors.add(entry);
        }
        ProblemDetail problem = buildProblem(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Validation failed",
            "Validation failed"
        );
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Handled ConstraintViolationException", ex);
        List<Map<String, String>> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("field", violation.getPropertyPath().toString());
            entry.put("message", violation.getMessage());
            errors.add(entry);
        }
        ProblemDetail problem = buildProblem(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Validation failed",
            "Validation failed"
        );
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Handled HttpMessageNotReadableException", ex);
        ProblemDetail problem = buildProblem(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_JSON",
            "Malformed JSON",
            "Malformed JSON"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = buildProblem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Unexpected error",
            "Internal Server Error"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ProblemDetail buildProblem(HttpStatusCode status, String code, String detail, String title) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (title != null && !title.isBlank()) {
            problem.setTitle(title);
        }
        problem.setProperty("code", code);
        return problem;
    }

    private String toCode(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return httpStatus.name();
        }
        return "ERROR";
    }

    private String toTitle(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return httpStatus.getReasonPhrase();
        }
        return "Error";
    }
}
