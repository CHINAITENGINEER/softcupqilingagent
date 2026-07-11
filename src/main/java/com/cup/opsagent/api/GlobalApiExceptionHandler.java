package com.cup.opsagent.api;

import com.cup.opsagent.rag.RagProviderErrorCode;
import com.cup.opsagent.rag.RagProviderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(SecurityException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", exception.getMessage(), request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", safeMessage(exception, "resource not found"), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "STATE_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage());
        }
        ApiErrorResponse response = ApiErrorResponse.of(
                "VALIDATION_FAILED",
                "request validation failed",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RagProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleRagProviderException(RagProviderException exception, HttpServletRequest request) {
        HttpStatus status = ragProviderStatus(exception.code());
        return error(status, exception.code().name(), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal server error", request);
    }

    private HttpStatus ragProviderStatus(RagProviderErrorCode code) {
        if (code == null) {
            return HttpStatus.BAD_GATEWAY;
        }
        return switch (code) {
            case RAG_PROVIDER_MISCONFIGURED -> HttpStatus.BAD_REQUEST;
            case RAG_PROVIDER_UNAUTHORIZED -> HttpStatus.BAD_GATEWAY;
            case RAG_PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case RAG_PROVIDER_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case RAG_PROVIDER_BAD_REQUEST -> HttpStatus.BAD_GATEWAY;
            case RAG_PROVIDER_SERVER_ERROR, RAG_PROVIDER_BAD_RESPONSE, RAG_PROVIDER_UNSUPPORTED -> HttpStatus.BAD_GATEWAY;
        };
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, safeMessage(message, status.getReasonPhrase()), status.value(), request.getRequestURI()));
    }

    private String safeMessage(Throwable throwable, String fallback) {
        return throwable == null ? fallback : safeMessage(throwable.getMessage(), fallback);
    }

    private String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
