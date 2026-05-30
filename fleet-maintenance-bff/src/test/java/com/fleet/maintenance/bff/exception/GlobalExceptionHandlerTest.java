package com.fleet.maintenance.bff.exception;

import com.fleet.maintenance.domain.exception.DomainValidationException;
import com.fleet.maintenance.domain.exception.IllegalStateTransitionException;
import com.fleet.maintenance.domain.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.fleet.maintenance.bff.dto.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_INTERNAL_ERROR = 500;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithProblemDetail() {
        ResponseEntity<ProblemDetail> response =
            handler.handleNotFound(new NotFoundException("Request not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HTTP_NOT_FOUND);
        assertThat(response.getBody().getTitle()).isEqualTo("Not Found");
        assertThat(response.getBody().getDetail()).isEqualTo("Request not found");
        assertThat(response.getBody().getType().toString()).contains("not-found");
    }

    @Test
    void handleStateConflict_returns409() {
        ResponseEntity<ProblemDetail> response =
            handler.handleStateConflict(new IllegalStateTransitionException("Invalid transition"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HTTP_CONFLICT);
        assertThat(response.getBody().getType().toString()).contains("state-conflict");
    }

    @Test
    void handleDomainValidation_returns400() {
        ResponseEntity<ProblemDetail> response =
            handler.handleDomainValidation(new DomainValidationException("remarks required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HTTP_BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("remarks required");
    }

    @Test
    void handleGeneric_returns500() {
        ResponseEntity<ProblemDetail> response =
            handler.handleGeneric(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HTTP_INTERNAL_ERROR);
    }

    @Test
    void handleIllegalArg_returns400() {
        ResponseEntity<ProblemDetail> response =
            handler.handleIllegalArg(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HTTP_BAD_REQUEST);
    }

    @Test
    void handleBeanValidation_returns400WithViolations() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(target, "target");
        binding.addError(new FieldError("target", "vehicleId", null, false,
            null, null, "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ProblemDetail> response = handler.handleBeanValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getViolations()).isNotEmpty();
        assertThat(response.getBody().getViolations().get(0).getField()).isEqualTo("vehicleId");
    }
}
