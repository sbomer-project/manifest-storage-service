package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import java.time.Instant;

import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.ErrorResponse;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception mapper for IllegalArgumentException.
 * Maps validation errors from the service layer to HTTP 400 Bad Request.
 */
@Provider
@Slf4j
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(IllegalArgumentException e) {
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";
        log.warn("Validation error at {}: {}", path, e.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .path(path)
                .build();
        
        return Response.status(Response.Status.BAD_REQUEST)
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}

