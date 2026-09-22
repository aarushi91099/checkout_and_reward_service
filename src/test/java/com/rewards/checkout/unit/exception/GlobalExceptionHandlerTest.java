package com.rewards.checkout.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.rewards.checkout.exception.ApiException;
import com.rewards.checkout.exception.ErrorCode;
import com.rewards.checkout.exception.GlobalExceptionHandler;
import com.rewards.checkout.web.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsApiExceptionToItsDeclaredStatusAndCode() {
        ApiException ex = new ApiException(ErrorCode.INSUFFICIENT_INVENTORY, "not enough stock");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_INVENTORY");
        assertThat(response.getBody().message()).isEqualTo("not enough stock");
    }

    @Test
    void mapsEveryErrorCodeToANonNullStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getStatus()).isNotNull();
        }
    }

    @Test
    void mapsDataIntegrityViolationToIdempotencyKeyConflict() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }
}
