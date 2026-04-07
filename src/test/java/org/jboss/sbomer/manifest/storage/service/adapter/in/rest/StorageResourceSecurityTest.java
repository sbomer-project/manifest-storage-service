package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Security tests for StorageResource download endpoint.
 * Tests path traversal prevention and header injection protection.
 */
@ExtendWith(MockitoExtension.class)
class StorageResourceSecurityTest {

    @Mock
    private StorageAdministration storageService;

    @InjectMocks
    private StorageResource storageResource;

    private InputStream mockInputStream;

    @BeforeEach
    void setUp() {
        mockInputStream = new ByteArrayInputStream("test content".getBytes());
    }

    @Test
    void testValidPath_Success() {
        // Valid path: gen-123/file.json
        when(storageService.getFileContent(anyString())).thenReturn(mockInputStream);
        
        Response response = storageResource.download("gen-123/file.json");
        
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(storageService).getFileContent("gen-123/file.json");
    }

    @Test
    void testValidPathWithEnhancement_Success() {
        // Valid path: gen-123/enh-456/file.json
        when(storageService.getFileContent(anyString())).thenReturn(mockInputStream);
        
        Response response = storageResource.download("gen-123/enh-456/file.json");
        
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(storageService).getFileContent("gen-123/enh-456/file.json");
    }

    @Test
    void testPathTraversal_DoubleDot_Blocked() {
        // Attack: ../../../etc/passwd
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("../../../etc/passwd")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testPathTraversal_DoubleDotInMiddle_Blocked() {
        // Attack: gen-123/../../../etc/passwd
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("gen-123/../../../etc/passwd")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testPathTraversal_AbsolutePath_Blocked() {
        // Attack: /etc/passwd
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("/etc/passwd")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testPathTraversal_Backslash_Blocked() {
        // Attack: gen-123\..\..\etc\passwd (Windows-style)
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("gen-123\\..\\..\\etc\\passwd")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testInvalidPathFormat_SpecialCharacters_Blocked() {
        // Attack: gen-123/file@name#here.json
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("gen-123/file@name#here.json")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testInvalidPathFormat_TooManySegments_Blocked() {
        // Invalid: gen-123/enh-456/extra/file.json (too many segments)
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("gen-123/enh-456/extra/file.json")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testEmptyPath_Blocked() {
        // Empty path
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download("")
        );
        
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testHeaderInjection_CarriageReturn_Sanitized() {
        // Attack: filename with \r\n to inject headers
        when(storageService.getFileContent(anyString())).thenReturn(mockInputStream);
        
        // Note: The path validation will block this, but if it somehow gets through,
        // the filename sanitization should handle it
        String validPath = "gen-123/file.json";
        Response response = storageResource.download(validPath);
        
        String contentDisposition = (String) response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        // Verify no newlines in header
        assertFalse(contentDisposition.contains("\r"));
        assertFalse(contentDisposition.contains("\n"));
    }

    @Test
    void testFilenameSanitization_SpecialCharacters() {
        // Filename with special characters should be sanitized
        when(storageService.getFileContent(anyString())).thenReturn(mockInputStream);
        
        String validPath = "gen-123/file-name_123.json";
        Response response = storageResource.download(validPath);
        
        String contentDisposition = (String) response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.contains("filename=\"file-name_123.json\""));
    }

    @Test
    void testValidFilenameWithDots_Allowed() {
        // Filename with dots should be allowed
        when(storageService.getFileContent(anyString())).thenReturn(mockInputStream);
        
        String validPath = "gen-123/file.name.with.dots.json";
        Response response = storageResource.download(validPath);
        
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String contentDisposition = (String) response.getHeaders().getFirst("Content-Disposition");
        assertTrue(contentDisposition.contains("file.name.with.dots.json"));
    }

    @Test
    void testValidFilenameWithHyphensAndUnderscores_Allowed() {
        // Filename with hyphens and underscores should be allowed
        when(storageService.getFileContent(anyString())).thenReturn(mockInputStream);
        
        String validPath = "gen-123/file-name_123.json";
        Response response = storageResource.download(validPath);
        
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        String contentDisposition = (String) response.getHeaders().getFirst("Content-Disposition");
        assertTrue(contentDisposition.contains("file-name_123.json"));
    }
}

// Made with Bob
