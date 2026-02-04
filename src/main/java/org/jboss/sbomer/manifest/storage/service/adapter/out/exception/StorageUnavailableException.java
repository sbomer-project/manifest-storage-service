package org.jboss.sbomer.manifest.storage.service.adapter.out.exception;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * Thrown when the storage service is temporarily unavailable.
 * Maps to HTTP 503 Service Unavailable.
 */
public class StorageUnavailableException extends StorageException {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause, SERVICE_UNAVAILABLE);
    }
}