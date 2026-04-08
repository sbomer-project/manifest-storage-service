package org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardized error response DTO for all REST endpoints.
 * Provides consistent error format across the API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private Integer status;
    private String error;
    private String message;
    private Instant timestamp;
    private String path;
    private String traceId;
    
    // Default constructor for Jackson
    public ErrorResponse() {
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public Integer getStatus() {
        return status;
    }
    
    public String getError() {
        return error;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    // Setters for Jackson
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    // Builder class
    public static class Builder {
        private final ErrorResponse response = new ErrorResponse();
        
        public Builder status(Integer status) {
            response.status = status;
            return this;
        }
        
        public Builder error(String error) {
            response.error = error;
            return this;
        }
        
        public Builder message(String message) {
            response.message = message;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            response.timestamp = timestamp;
            return this;
        }
        
        public Builder path(String path) {
            response.path = path;
            return this;
        }
        
        public Builder traceId(String traceId) {
            response.traceId = traceId;
            return this;
        }
        
        public ErrorResponse build() {
            return response;
        }
    }
}

