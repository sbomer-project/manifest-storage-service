package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import java.time.Instant;

import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.ErrorResponse;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception mapper for WebApplicationException.
 * Handles validation errors and other JAX-RS exceptions with proper status codes.
 */
@Provider
@Slf4j
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        String path = uriInfo != null ? uriInfo.getPath() : "unknown";
        
        log.warn("WebApplicationException: {} - {} at {}", status, e.getMessage(), path);
        
        ErrorResponse error = ErrorResponse.builder()
                .status(status)
                .error(Response.Status.fromStatusCode(status).getReasonPhrase())
                .message(e.getMessage())
                .timestamp(Instant.now())
                .path(path)
                .build();
        
        return Response.status(status)
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}

