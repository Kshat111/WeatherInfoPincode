package com.example.weather.api;

import com.example.weather.exception.PincodeNotFoundException;
import com.example.weather.exception.UpstreamServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PincodeNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(PincodeNotFoundException ex, HttpServletRequest req) {
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<Object> handleUpstream(UpstreamServiceException ex, HttpServletRequest req) {
        return errorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleBadRequest(MethodArgumentNotValidException ex, HttpServletRequest req) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid request", req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAll(Exception ex, HttpServletRequest req) {
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", req.getRequestURI());
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        return new ResponseEntity<>(body, status);
    }
}
