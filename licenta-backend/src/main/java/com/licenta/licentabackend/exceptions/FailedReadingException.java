package com.licenta.licentabackend.exceptions;

public class FailedReadingException extends RuntimeException {
    public FailedReadingException(String message) {
        super(message);
    }
}
