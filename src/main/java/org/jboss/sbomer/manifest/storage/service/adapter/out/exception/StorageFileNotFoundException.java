package org.jboss.sbomer.manifest.storage.service.adapter.out.exception;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Thrown when a requested file does not exist in storage.
 * Maps to HTTP 404 Not Found.
 */
public class StorageFileNotFoundException extends StorageException {
    public StorageFileNotFoundException(String message, Throwable cause) {
        super(message, cause, NOT_FOUND);
    }
}