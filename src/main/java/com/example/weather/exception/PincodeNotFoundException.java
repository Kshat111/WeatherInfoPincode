package com.example.weather.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PincodeNotFoundException extends RuntimeException {
    public PincodeNotFoundException(String message) {
        super(message);
    }
}
