package org.jboss.sbomer.manifest.storage.service.adapter.out.exception;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * Thrown when a storage key/path is invalid or malformed.
 * Maps to HTTP 400 Bad Request.
 */
public class StorageKeyInvalidException extends StorageException {
    public StorageKeyInvalidException(String key, String reason) {
        super("Storage key invalid '" + key + "': " + reason, BAD_REQUEST);
    }
}