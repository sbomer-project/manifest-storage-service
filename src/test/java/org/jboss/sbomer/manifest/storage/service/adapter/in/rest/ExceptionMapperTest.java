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
        // 404 errors go through StorageExceptionMapper for unified handling
        String message = "File not found: gen-1/bom.json";
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageFileNotFoundException(message, null));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/bom.json")
                .then()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", equalTo(message))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testStorageAccessDeniedReturnsStructuredError() {
        // 403 errors go through StorageExceptionMapper
        String message = "Access denied to storage bucket";
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageAccessException(message, null));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/bom.json")
                .then()
                .statusCode(FORBIDDEN.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(403))
                .body("error", equalTo("Forbidden"))
                .body("message", equalTo(message))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testUnhandledExceptionReturnsStructuredError() {
        // Generic exceptions go through GenericExceptionMapper
        when(storageService.getFileContent(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/bom.json")
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .contentType("application/json")
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

    @Test
    void testDownloadFileNotFoundReturnsJsonError() {
        // This test verifies that 404 errors from download endpoint
        // return proper JSON ErrorResponse with correct Content-Type
        String message = "File not found: gen-1/missing.json";
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageFileNotFoundException(message, null));
        
        given()
                .when().get("/api/v1/storage/content/gen-1/missing.json")
                .then()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"))
                .body("message", equalTo(message))
                .body("timestamp", notNullValue())
                .body("path", equalTo("/api/v1/storage/content/gen-1/missing.json"));
    }

    @Test
    void testPathValidationReturnsJsonError() {
        // This test verifies that path validation errors return 400 with JSON
        // Use a path that reaches our endpoint but fails validation
        given()
                .when().get("/api/v1/storage/content/gen-1/../etc/passwd")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(400))
                .body("error", equalTo("Bad Request"))
                .body("message", equalTo("Invalid path: path traversal not allowed"))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testInvalidPathFormatReturnsJsonError() {
        // This test verifies that invalid path format returns 400 with JSON
        given()
                .when().get("/api/v1/storage/content/invalid@path")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType("application/json")
                .body("status", equalTo(400))
                .body("error", equalTo("Bad Request"))
                .body("message", equalTo("Invalid path format"))
                .body("timestamp", notNullValue())
                .body("path", notNullValue());
    }

    @Test
    void testAllErrorResponsesHaveCorrectContentType() {
        // This test verifies that all error responses have Content-Type: application/json
        
        // Test 404 - handled by StorageExceptionMapper
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageFileNotFoundException("Not found", null));
        given()
                .when().get("/api/v1/storage/content/gen-1/test.json")
                .then()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType("application/json");

        // Test 403 - handled by StorageExceptionMapper
        when(storageService.getFileContent(anyString()))
                .thenThrow(new StorageAccessException("Access denied", null));
        given()
                .when().get("/api/v1/storage/content/gen-1/test.json")
                .then()
                .statusCode(FORBIDDEN.getStatusCode())
                .contentType("application/json");

        // Test 400 (validation) - handled by WebApplicationExceptionMapper
        given()
                .when().get("/api/v1/storage/content/gen-1/../test.json")
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType("application/json");

        // Test 500 (generic) - handled by GenericExceptionMapper
        when(storageService.getFileContent(anyString()))
                .thenThrow(new RuntimeException("Unexpected"));
        given()
                .when().get("/api/v1/storage/content/gen-1/test.json")
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
                .contentType("application/json");
    }
}
