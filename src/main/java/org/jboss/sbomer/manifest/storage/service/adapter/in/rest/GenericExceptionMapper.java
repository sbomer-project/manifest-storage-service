package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import java.time.Instant;

import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.ErrorResponse;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Catch-all exception mapper for unhandled exceptions.
 * Returns structured error response.
 */
@Provider
@Slf4j
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception e) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";
        log.error("Unhandled exception at {}: {}", path, e.getMessage(), e);
        
        String safeMessage = "An unexpected error occurred.";
        
        ErrorResponse error = ErrorResponse.builder()
                .status(500)
                .error("Internal Server Error")
                .message(safeMessage)
                .timestamp(Instant.now())
                .path(path)
                .build();
        
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}