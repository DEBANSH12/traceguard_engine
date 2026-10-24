package com.debansh.traceguard.exception;

public class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException(Long id) {
        super("IncidentGroup with ID " + id + " was not found");
    }
}
