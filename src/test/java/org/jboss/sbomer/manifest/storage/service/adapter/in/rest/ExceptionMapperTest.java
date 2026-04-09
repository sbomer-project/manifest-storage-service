package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.ErrorResponse;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageAccessException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageUnavailableException;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Unit tests for exception mappers.
 * Tests that exception mappers correctly convert exceptions to structured error responses.
 */
@ExtendWith(MockitoExtension.class)
class ExceptionMapperTest {

    @Mock
    StorageAdministration storageService;

    StorageResource storageResource;
    StorageExceptionMapper storageExceptionMapper;
    GenericExceptionMapper genericExceptionMapper;
    WebApplicationExceptionMapper webApplicationExceptionMapper;
    IllegalArgumentExceptionMapper illegalArgumentExceptionMapper;

    @BeforeEach
    void setUp() {
        storageResource = new StorageResource(storageService, 100, 10);
        storageExceptionMapper = new StorageExceptionMapper();
        genericExceptionMapper = new GenericExceptionMapper();
        webApplicationExceptionMapper = new WebApplicationExceptionMapper();
        illegalArgumentExceptionMapper = new IllegalArgumentExceptionMapper();
    }

    @Test
    void testStorageFileNotFoundReturnsStructuredError() {
        // Given
        String message = "File not found: gen-1/bom.json";
        StorageFileNotFoundException exception = new StorageFileNotFoundException(message, null);

        // When
        Response response = storageExceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(404, error.getStatus());
        assertEquals("Not Found", error.getError());
        assertEquals(message, error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getPath());
    }

    @Test
    void testStorageAccessDeniedReturnsStructuredError() {
        // Given
        String message = "Access denied to storage bucket";
        StorageAccessException exception = new StorageAccessException(message, null);

        // When
        Response response = storageExceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(403, error.getStatus());
        assertEquals("Forbidden", error.getError());
        assertEquals(message, error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getPath());
    }

    @Test
    void testStorageUnavailableReturnsStructuredError() {
        // Given
        String message = "Storage service unavailable";
        StorageUnavailableException exception = new StorageUnavailableException(message, null);

        // When
        Response response = storageExceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(503, error.getStatus());
        assertEquals("Service Unavailable", error.getError());
        assertEquals(message, error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getPath());
    }

    @Test
    void testUnhandledExceptionReturnsStructuredError() {
        // Given
        RuntimeException exception = new RuntimeException("Unexpected error");

        // When
        Response response = genericExceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(500, error.getStatus());
        assertEquals("Internal Server Error", error.getError());
        assertEquals("An unexpected error occurred.", error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getPath());
    }

    @Test
    void testIllegalArgumentExceptionReturnsBadRequest() {
        // Given
        String message = "Files list cannot be null or empty";
        IllegalArgumentException exception = new IllegalArgumentException(message);

        // When
        Response response = illegalArgumentExceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(400, error.getStatus());
        assertEquals("Bad Request", error.getError());
        assertEquals(message, error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getPath());
    }

    @Test
    void testWebApplicationExceptionPreservesStatusCode() {
        // Given
        String message = "No files provided";
        WebApplicationException exception = new WebApplicationException(
            message, 
            Response.Status.BAD_REQUEST
        );

        // When
        Response response = webApplicationExceptionMapper.toResponse(exception);

        // Then
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
        
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(400, error.getStatus());
        assertEquals("Bad Request", error.getError());
        assertEquals(message, error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNotNull(error.getPath());
    }

    @Test
    void testDownloadFileNotFoundPropagatesException() {
        // Given
        String message = "File not found: gen-1/missing.json";
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageFileNotFoundException(message, null));

        // When/Then - exception should propagate to exception mapper
        assertThrows(StorageFileNotFoundException.class, () -> {
            storageResource.download("gen-1/missing.json");
        });
    }

    @Test
    void testPathValidationThrowsWebApplicationException() {
        // When/Then - path traversal should throw WebApplicationException
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            storageResource.download("gen-1/../etc/passwd");
        });
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
    }

    @Test
    void testInvalidPathFormatThrowsWebApplicationException() {
        // When/Then - invalid path format should throw WebApplicationException
        WebApplicationException exception = assertThrows(WebApplicationException.class, () -> {
            storageResource.download("invalid@path");
        });
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
    }

    @Test
    void testAllExceptionMappersReturnJsonContentType() {
        // Test that all exception mappers return application/json content type
        
        // StorageExceptionMapper
        Response response1 = storageExceptionMapper.toResponse(
            new StorageFileNotFoundException("Not found", null)
        );
        assertEquals("application/json", response1.getMediaType().toString());

        Response response2 = storageExceptionMapper.toResponse(
            new StorageAccessException("Access denied", null)
        );
        assertEquals("application/json", response2.getMediaType().toString());

        // GenericExceptionMapper
        Response response3 = genericExceptionMapper.toResponse(
            new RuntimeException("Unexpected")
        );
        assertEquals("application/json", response3.getMediaType().toString());

        // IllegalArgumentExceptionMapper
        Response response4 = illegalArgumentExceptionMapper.toResponse(
            new IllegalArgumentException("Invalid argument")
        );
        assertEquals("application/json", response4.getMediaType().toString());

        // WebApplicationExceptionMapper
        Response response5 = webApplicationExceptionMapper.toResponse(
            new WebApplicationException("Bad request", Response.Status.BAD_REQUEST)
        );
        assertEquals("application/json", response5.getMediaType().toString());
    }

    @Test
    void testDownloadSuccessReturnsInputStream() {
        // Given
        byte[] content = "test content".getBytes();
        when(storageService.getFileContent(anyString()))
                .thenReturn(new ByteArrayInputStream(content));

        // When
        Response response = storageResource.download("gen-1/test.json");

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("application/octet-stream", response.getMediaType().toString());
        assertNotNull(response.getEntity());
        assertTrue(response.getEntity() instanceof ByteArrayInputStream);
    }
}
