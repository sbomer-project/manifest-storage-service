package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class StorageResourceTest {

    @Mock
    StorageAdministration storageService;

    StorageResource storageResource;

    @TempDir
    Path tempDir;

    private static final String GENERATION_ID = "gen-123";
    private static final String ENHANCEMENT_ID = "enh-456";

    @BeforeEach
    void setUp() {
        storageResource = new StorageResource(storageService, 100, 10);
    }

    @Test
    void testUploadGeneration_Success() throws IOException {
        // Given
        FileUpload upload = createMockFileUpload("bom.json", "application/json", 100L);
        List<FileUpload> uploads = Arrays.asList(upload);

        Map<String, String> expectedResult = new HashMap<>();
        expectedResult.put("bom.json", "https://api.example.com/api/v1/storage/content/gen-123/bom.json");

        when(storageService.storeGenerationSboms(eq(GENERATION_ID), anyList()))
                .thenReturn(expectedResult);

        // When
        Response response = storageResource.uploadGeneration(GENERATION_ID, uploads);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) response.getEntity();
        assertEquals(expectedResult, result);

        // Verify the service was called with correct parameters
        ArgumentCaptor<List<SbomFile>> filesCaptor = ArgumentCaptor.forClass(List.class);
        verify(storageService).storeGenerationSboms(eq(GENERATION_ID), filesCaptor.capture());
        
        List<SbomFile> capturedFiles = filesCaptor.getValue();
        assertEquals(1, capturedFiles.size());
        assertEquals("bom.json", capturedFiles.get(0).getFilename());
        assertEquals("application/json", capturedFiles.get(0).getContentType());
        assertEquals(100L, capturedFiles.get(0).getSize());
    }

    @Test
    void testUploadGeneration_MultipleFiles() throws IOException {
        // Given
        FileUpload upload1 = createMockFileUpload("bom1.json", "application/json", 100L);
        FileUpload upload2 = createMockFileUpload("bom2.json", "application/json", 200L);
        List<FileUpload> uploads = Arrays.asList(upload1, upload2);

        Map<String, String> expectedResult = new HashMap<>();
        expectedResult.put("bom1.json", "https://api.example.com/api/v1/storage/content/gen-123/bom1.json");
        expectedResult.put("bom2.json", "https://api.example.com/api/v1/storage/content/gen-123/bom2.json");

        when(storageService.storeGenerationSboms(eq(GENERATION_ID), anyList()))
                .thenReturn(expectedResult);

        // When
        Response response = storageResource.uploadGeneration(GENERATION_ID, uploads);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        ArgumentCaptor<List<SbomFile>> filesCaptor = ArgumentCaptor.forClass(List.class);
        verify(storageService).storeGenerationSboms(eq(GENERATION_ID), filesCaptor.capture());
        
        List<SbomFile> capturedFiles = filesCaptor.getValue();
        assertEquals(2, capturedFiles.size());
    }

    @Test
    void testUploadGeneration_NoFiles() {
        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.uploadGeneration(GENERATION_ID, null)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("No files provided", exception.getMessage());
        verify(storageService, never()).storeGenerationSboms(anyString(), anyList());
    }

    @Test
    void testUploadGeneration_EmptyFilesList() {
        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.uploadGeneration(GENERATION_ID, Arrays.asList())
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("No files provided", exception.getMessage());
        verify(storageService, never()).storeGenerationSboms(anyString(), anyList());
    }

    @Test
    void testUploadGeneration_TooManyFiles() {
        // Given - Create list with more than maxFilesPerBatch (10)
        List<FileUpload> uploads = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            FileUpload upload = mock(FileUpload.class, withSettings().lenient());
            when(upload.fileName()).thenReturn("file" + i + ".json");
            when(upload.contentType()).thenReturn("application/json");
            when(upload.size()).thenReturn(100L);
            uploads.add(upload);
        }

        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.uploadGeneration(GENERATION_ID, uploads)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertTrue(exception.getMessage().contains("Too many files"));
        verify(storageService, never()).storeGenerationSboms(anyString(), anyList());
    }

    @Test
    void testUploadGeneration_FileTooLarge() {
        // Given - file size exceeds 100MB limit
        long fileSizeBytes = 101L * 1024L * 1024L;
        FileUpload upload = mock(FileUpload.class, withSettings().lenient());
        when(upload.fileName()).thenReturn("large.json");
        when(upload.contentType()).thenReturn("application/json");
        when(upload.size()).thenReturn(fileSizeBytes);
        List<FileUpload> uploads = Arrays.asList(upload);

        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.uploadGeneration(GENERATION_ID, uploads)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertTrue(exception.getMessage().contains("exceeds maximum size"));
        verify(storageService, never()).storeGenerationSboms(anyString(), anyList());
    }

    @Test
    void testUploadGeneration_EmptyFilename() {
        // Given
        FileUpload upload = mock(FileUpload.class, withSettings().lenient());
        when(upload.fileName()).thenReturn("");
        when(upload.contentType()).thenReturn("application/json");
        when(upload.size()).thenReturn(100L);
        List<FileUpload> uploads = Arrays.asList(upload);

        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.uploadGeneration(GENERATION_ID, uploads)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("Filename cannot be empty", exception.getMessage());
        verify(storageService, never()).storeGenerationSboms(anyString(), anyList());
    }

    @Test
    void testUploadEnhancement_Success() throws IOException {
        // Given
        FileUpload upload = createMockFileUpload("enhanced.json", "application/json", 150L);
        List<FileUpload> uploads = Arrays.asList(upload);

        Map<String, String> expectedResult = new HashMap<>();
        expectedResult.put("enhanced.json", "https://api.example.com/api/v1/storage/content/gen-123/enh-456/enhanced.json");

        when(storageService.storeEnhancementSboms(eq(GENERATION_ID), eq(ENHANCEMENT_ID), anyList()))
                .thenReturn(expectedResult);

        // When
        Response response = storageResource.uploadEnhancement(GENERATION_ID, ENHANCEMENT_ID, uploads);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) response.getEntity();
        assertEquals(expectedResult, result);

        verify(storageService).storeEnhancementSboms(eq(GENERATION_ID), eq(ENHANCEMENT_ID), anyList());
    }

    @Test
    void testUploadEnhancement_NoFiles() {
        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.uploadEnhancement(GENERATION_ID, ENHANCEMENT_ID, null)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertEquals("No files provided", exception.getMessage());
        verify(storageService, never()).storeEnhancementSboms(anyString(), anyString(), anyList());
    }

    @Test
    void testDownload_Success() {
        // Given
        String path = "gen-123/bom.json";
        InputStream mockStream = new ByteArrayInputStream("test content".getBytes());
        when(storageService.getFileContent(path)).thenReturn(mockStream);

        // When
        Response response = storageResource.download(path);

        // Then
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity() instanceof InputStream);
        assertTrue(response.getHeaderString("Content-Disposition").contains("attachment"));
        assertTrue(response.getHeaderString("Content-Disposition").contains("bom.json"));
        verify(storageService).getFileContent(path);
    }

    @Test
    void testDownload_FileNotFound() {
        // Given
        String path = "gen-123/missing.json";
        when(storageService.getFileContent(path))
                .thenThrow(new StorageFileNotFoundException("File not found: " + path, null));

        // When & Then - Exception propagates to StorageExceptionMapper
        StorageFileNotFoundException exception = assertThrows(
                StorageFileNotFoundException.class,
                () -> storageResource.download(path)
        );
        assertEquals("File not found: " + path, exception.getMessage());
        verify(storageService).getFileContent(path);
    }

    @Test
    void testDownload_InternalError() {
        // Given
        String path = "gen-123/error.json";
        when(storageService.getFileContent(path))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then - Exception propagates to GenericExceptionMapper
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageResource.download(path)
        );
        assertEquals("Unexpected error", exception.getMessage());
        verify(storageService).getFileContent(path);
    }

    @Test
    void testDownload_InvalidPath_PathTraversal() {
        // Given
        String path = "../etc/passwd";

        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download(path)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        assertTrue(exception.getMessage().contains("path traversal"));
        verify(storageService, never()).getFileContent(anyString());
    }

    @Test
    void testDownload_InvalidPath_InvalidFormat() {
        // Given
        String path = "gen-123/../../file.json";

        // When & Then
        WebApplicationException exception = assertThrows(
                WebApplicationException.class,
                () -> storageResource.download(path)
        );
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), exception.getResponse().getStatus());
        verify(storageService, never()).getFileContent(anyString());
    }


    @Test
    void testUploadGeneration_ServiceThrowsException() throws IOException {
        // Given
        FileUpload upload = createMockFileUpload("bom.json", "application/json", 100L);
        List<FileUpload> uploads = Arrays.asList(upload);

        when(storageService.storeGenerationSboms(eq(GENERATION_ID), anyList()))
                .thenThrow(new RuntimeException("Storage service error"));

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageResource.uploadGeneration(GENERATION_ID, uploads)
        );
        assertEquals("Storage service error", exception.getMessage());
    }

    private FileUpload createMockFileUpload(String filename, String contentType, long size) throws IOException {
        FileUpload upload = mock(FileUpload.class);
        when(upload.fileName()).thenReturn(filename);
        when(upload.contentType()).thenReturn(contentType);
        when(upload.size()).thenReturn(size);
        
        // Create a temporary file
        Path tempFile = tempDir.resolve(filename.isEmpty() ? "temp" : filename);
        Files.write(tempFile, "test content".getBytes());
        when(upload.uploadedFile()).thenReturn(tempFile);
        
        return upload;
    }
}
