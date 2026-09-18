package com.rewin.itsm.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns exceptions into {@link ApiError}. Unhandled exceptions are logged with
 * their stack trace but answered with a generic message, so internals never
 * reach a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError body = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "Validation failed",
            "One or more fields are invalid.", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraintViolation(ConstraintViolationException ex) {
        return status(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
    }

    /**
     * The client sent something this endpoint cannot read: a body that is not
     * valid JSON, a path variable of the wrong type, or a required parameter
     * that is absent. All of them are the caller's mistake, so none of them may
     * fall through to the catch-all and be reported as a server fault.
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> onUnreadable(Exception ex) {
        return status(HttpStatus.BAD_REQUEST, "Malformed request",
            "The request body or a parameter could not be read.");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> onBadRequest(BadRequestException ex) {
        return status(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(NotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> onConflict(ConflictException ex) {
        return status(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiError> onBadCredentials(Exception ex) {
        // One message for both cases: never reveal whether the account exists.
        return status(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password.");
    }

    /**
     * A path that matches no controller, and a path matched with the wrong verb.
     * Without these two, both fall through to the catch-all and are answered
     * with 500 - which tells a caller the server broke when in fact the request
     * was simply wrong.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> onNoHandler(Exception ex) {
        return status(HttpStatus.NOT_FOUND, "Not found", "No endpoint matches that path.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> onMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return status(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
            "That endpoint does not support " + ex.getMethod() + ".");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ex) {
        return status(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return status(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
            "Something went wrong. Please try again.");
    }

    private ResponseEntity<ApiError> status(HttpStatus httpStatus, String error, String message) {
        return ResponseEntity.status(httpStatus).body(ApiError.of(httpStatus.value(), error, message));
    }
}
