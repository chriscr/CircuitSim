package com.circuitsim.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", Map.of("code", "INVALID_JSON", "message", "Request body must be valid JSON.")));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = status == HttpStatus.BAD_REQUEST ? "INVALID_REQUEST"
                : status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "REQUEST_FAILED";
        return ResponseEntity.status(status).body(Map.of("error",
                Map.of("code", code, "message", ex.getReason() == null ? "Request failed" : ex.getReason())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error",
                Map.of("code", "INTERNAL_SERVICE_ERROR", "message", "The API could not complete the request.")));
    }
}
