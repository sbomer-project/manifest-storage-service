package org.jboss.sbomer.manifest.storage.service.adapter.out.exception;

import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import jakarta.ws.rs.core.Response;

/**
 * Base exception for storage operations.
 * Contains HTTP status code for REST layer mapping.
 */
public class StorageException extends RuntimeException {
    private final Response.Status status;

    public StorageException(String message, Throwable cause) {
        this(message, cause, INTERNAL_SERVER_ERROR);
    }

    public StorageException(String message, Response.Status status) {
        super(message);
        this.status = status;
    }

    public StorageException(String message, Throwable cause, Response.Status status) {
        super(message, cause);
        this.status = status;
    }

    public Response.Status getStatus() {
        return status;
    }
}