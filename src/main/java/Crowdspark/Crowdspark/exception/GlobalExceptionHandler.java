// src/main/java/Crowdspark/Crowdspark/exception/GlobalExceptionHandler.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • Added ConstraintViolationException handler — fires for @Validated on
//     @RequestParam / @PathVariable annotations (different from @RequestBody
//     validation which uses MethodArgumentNotValidException)
//   • Added HttpMessageNotReadableException handler — fires when:
//       - The JSON body is malformed / unparseable
//       - A String field contains an invalid enum value
//   • Added MaxUploadSizeExceededException handler — fires when a multipart
//     upload exceeds spring.servlet.multipart.max-file-size
//   • Added MethodArgumentTypeMismatchException handler — fires when a path
//     variable (e.g. {id}) cannot be cast to the declared param type

package Crowdspark.Crowdspark.exception;

import Crowdspark.Crowdspark.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Auth errors ──────────────────────────────────────────────────────────

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── @RequestBody bean-validation errors ──────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    // ── @Validated on @RequestParam / @PathVariable ───────────────────────────
    // Feature #25: was missing — caused unhandled 500 for invalid query params

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            // cv.getPropertyPath() looks like "methodName.paramName"
            String path = cv.getPropertyPath().toString();
            String param = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(param, cv.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    // ── Malformed JSON / unreadable request body ──────────────────────────────
    // Feature #25: was missing — caused unhandled 400 with Spring's default HTML error

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex) {

        String msg = "Request body is missing or contains invalid JSON";
        // Surface the root cause if it is an enum mismatch (more useful message)
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null
                && cause.getMessage().contains("not one of the values accepted")) {
            msg = "One or more fields contain an invalid value: " + cause.getMessage();
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(msg));
    }

    // ── File too large ────────────────────────────────────────────────────────
    // Feature #25: was missing — multipart > max-file-size returned raw Tomcat error

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex) {

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(
                        "File size exceeds the maximum allowed limit of 20 MB"));
    }

    // ── Path-variable type mismatch ───────────────────────────────────────────
    // e.g. GET /api/projects/abc when {id} is Long

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String msg = String.format(
                "'%s' is not a valid value for parameter '%s'",
                ex.getValue(), ex.getName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(msg));
    }

    // ── Access denied ─────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You don't have permission to access this"));
    }

    // ── Explicit HTTP status exceptions ───────────────────────────────────────

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(
            ResponseStatusException ex) {

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getReason()));
    }

    // ── Catch-all ─────────────────────────────────────────────────────────────
    // BUG FIX (Feature #25): this returned ex.getMessage() straight to the
    // client for ANY uncaught exception — which can include raw SQL fragments
    // (constraint names, column names), internal file paths, or other
    // implementation details a bad actor could use for reconnaissance. The
    // real message is still logged server-side; the client now gets a
    // generic, safe message instead.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Something went wrong. Please try again later."));
    }
}
