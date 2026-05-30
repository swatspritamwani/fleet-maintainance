package com.fleet.maintenance.bff.exception;

import com.fleet.maintenance.bff.dto.ProblemDetail;
import com.fleet.maintenance.bff.dto.ProblemDetailViolationsInner;
import com.fleet.maintenance.domain.exception.DomainValidationException;
import com.fleet.maintenance.domain.exception.IllegalStateTransitionException;
import com.fleet.maintenance.domain.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://fleet-maintenance.example.com/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleStateConflict(IllegalStateTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "state-conflict", "State Conflict", ex.getMessage());
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleDomainValidation(DomainValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ProblemDetailViolationsInner> violations = ex.getBindingResult().getAllErrors().stream()
            .filter(e -> e instanceof FieldError)
            .map(e -> {
                FieldError fe = (FieldError) e;
                ProblemDetailViolationsInner v = new ProblemDetailViolationsInner();
                v.setField(fe.getField());
                v.setMessage(fe.getDefaultMessage());
                v.setRejectedValue(fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null);
                return v;
            })
            .toList();
        ProblemDetail body = buildProblem(HttpStatus.BAD_REQUEST, "validation-error",
            "Validation Error", "One or more fields failed validation");
        body.setViolations(violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArg(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
            "Internal Server Error", "An unexpected error occurred");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String type, String title, String detail) {
        return ResponseEntity.status(status)
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(buildProblem(status, type, title, detail));
    }

    private ProblemDetail buildProblem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail pd = new ProblemDetail();
        pd.setType(URI.create(BASE_URI + type));
        pd.setTitle(title);
        pd.setStatus(status.value());
        pd.setDetail(detail);
        pd.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        return pd;
    }
}
