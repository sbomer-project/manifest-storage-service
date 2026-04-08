package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageAccessException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;
import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ExceptionMapperTest {

    @InjectMock
    StorageAdministration storageService;

    @Test
    void testStorageFileNotFoundReturnsStructuredError() {
        String message = "File not found: gen-1/bom.json";
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageFileNotFoundException(message, null));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/bom.json")
                .then()
                .statusCode(NOT_FOUND.getStatusCode())
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", equalTo(message))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testStorageAccessDeniedReturnsStructuredError() {
        String message = "Access denied to storage bucket";
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageAccessException(message, null));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/bom.json")
                .then()
                .statusCode(FORBIDDEN.getStatusCode())
                .body("status", equalTo(403))
                .body("error", equalTo("Forbidden"))
                .body("message", equalTo(message))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testUnhandledExceptionReturnsStructuredError() {
        when(storageService.getFileContent(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/bom.json")
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .body("status", equalTo(500))
                .body("error", equalTo("Internal Server Error"))
                .body("message", equalTo("An unexpected error occurred."))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testIllegalArgumentExceptionReturnsBadRequest() {
        // Test that IllegalArgumentException from service layer returns 400
        // Note: This test sends files to avoid the "No files provided" validation
        when(storageService.storeGenerationSboms(anyString(), any()))
                .thenThrow(new IllegalArgumentException("Files list cannot be null or empty"));
        
        given()
                .contentType("multipart/form-data")
                .multiPart("files", "test.json", "{}".getBytes(), "application/json")
                .when().post("/api/v1/storage/generations/gen-123")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(400))
                .body("error", equalTo("Bad Request"))
                .body("message", equalTo("Files list cannot be null or empty"))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testWebApplicationExceptionPreservesStatusCode() {
        // This test verifies that validation errors from StorageResource
        // return the correct status code (400) instead of 500
        given()
                .contentType("multipart/form-data")
                .when().post("/api/v1/storage/generations/gen-123")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(400))
                .body("error", equalTo("Bad Request"))
                .body("message", equalTo("No files provided"))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }
}
