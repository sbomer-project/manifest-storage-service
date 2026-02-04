package org.jboss.sbomer.manifest.storage.service.adapter.out.exception;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;

/**
 * Thrown when storage access is denied due to permissions or authentication issues.
 * Maps to HTTP 403 Forbidden.
 */
public class StorageAccessException extends StorageException {
    public StorageAccessException(String message, Throwable cause) {
        super(message, cause, FORBIDDEN);
    }
}