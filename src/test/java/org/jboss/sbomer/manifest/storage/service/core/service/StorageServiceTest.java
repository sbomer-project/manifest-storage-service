package org.jboss.sbomer.manifest.storage.service.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.spi.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    ObjectStorage objectStorage;

    @InjectMocks
    StorageService storageService;

    private static final String PUBLIC_API_URL = "https://api.example.com";
    private static final String GENERATION_ID = "gen-123";
    private static final String ENHANCEMENT_ID = "enh-456";

    @BeforeEach
    void setUp() {
        // Use reflection to set the publicApiUrl field
        try {
            var field = StorageService.class.getDeclaredField("publicApiUrl");
            field.setAccessible(true);
            field.set(storageService, PUBLIC_API_URL);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testStoreGenerationSboms_Success() {
        // Given
        InputStream content = new ByteArrayInputStream("test content".getBytes());
        SbomFile file = SbomFile.builder()
                .filename("bom.json")
                .contentType("application/json")
                .size(100L)
                .content(content)
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When
        Map<String, String> result = storageService.storeGenerationSboms(GENERATION_ID, files);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("bom.json"));
        assertEquals(PUBLIC_API_URL + "/api/v1/storage/content/gen-123/bom.json", result.get("bom.json"));
        
        verify(objectStorage).upload(
                eq("gen-123/bom.json"),
                eq(content),
                eq(100L),
                eq("application/json")
        );
    }

    @Test
    void testStoreGenerationSboms_MultipleFiles() {
        // Given
        InputStream content1 = new ByteArrayInputStream("content1".getBytes());
        InputStream content2 = new ByteArrayInputStream("content2".getBytes());
        
        SbomFile file1 = SbomFile.builder()
                .filename("bom1.json")
                .contentType("application/json")
                .size(100L)
                .content(content1)
                .build();
        
        SbomFile file2 = SbomFile.builder()
                .filename("bom2.json")
                .contentType("application/json")
                .size(200L)
                .content(content2)
                .build();
        
        List<SbomFile> files = Arrays.asList(file1, file2);

        // When
        Map<String, String> result = storageService.storeGenerationSboms(GENERATION_ID, files);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("bom1.json"));
        assertTrue(result.containsKey("bom2.json"));
        
        verify(objectStorage).upload(eq("gen-123/bom1.json"), eq(content1), eq(100L), eq("application/json"));
        verify(objectStorage).upload(eq("gen-123/bom2.json"), eq(content2), eq(200L), eq("application/json"));
    }

    @Test
    void testStoreGenerationSboms_NullGenerationId() {
        // Given
        SbomFile file = SbomFile.builder()
                .filename("bom.json")
                .contentType("application/json")
                .size(100L)
                .content(new ByteArrayInputStream("test".getBytes()))
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms(null, files)
        );
        assertEquals("generationId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testStoreGenerationSboms_EmptyGenerationId() {
        // Given
        SbomFile file = SbomFile.builder()
                .filename("bom.json")
                .contentType("application/json")
                .size(100L)
                .content(new ByteArrayInputStream("test".getBytes()))
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms("  ", files)
        );
        assertEquals("generationId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testStoreGenerationSboms_NullFiles() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, null)
        );
        assertEquals("Files list cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testStoreGenerationSboms_EmptyFiles() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, Arrays.asList())
        );
        assertEquals("Files list cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testStoreGenerationSboms_UploadFailure() {
        // Given
        InputStream content = new ByteArrayInputStream("test content".getBytes());
        SbomFile file = SbomFile.builder()
                .filename("bom.json")
                .contentType("application/json")
                .size(100L)
                .content(content)
                .build();
        List<SbomFile> files = Arrays.asList(file);

        doThrow(new RuntimeException("S3 error"))
                .when(objectStorage)
                .upload(anyString(), any(), anyLong(), anyString());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, files)
        );
        assertTrue(exception.getMessage().contains("Failed to upload file 'bom.json'"));
        assertTrue(exception.getCause().getMessage().contains("S3 error"));
    }

    @Test
    void testStoreEnhancementSboms_Success() {
        // Given
        InputStream content = new ByteArrayInputStream("test content".getBytes());
        SbomFile file = SbomFile.builder()
                .filename("enhanced.json")
                .contentType("application/json")
                .size(150L)
                .content(content)
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When
        Map<String, String> result = storageService.storeEnhancementSboms(GENERATION_ID, ENHANCEMENT_ID, files);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("enhanced.json"));
        assertEquals(
                PUBLIC_API_URL + "/api/v1/storage/content/gen-123/enh-456/enhanced.json",
                result.get("enhanced.json")
        );
        
        verify(objectStorage).upload(
                eq("gen-123/enh-456/enhanced.json"),
                eq(content),
                eq(150L),
                eq("application/json")
        );
    }

    @Test
    void testStoreEnhancementSboms_NullGenerationId() {
        // Given
        SbomFile file = SbomFile.builder()
                .filename("enhanced.json")
                .contentType("application/json")
                .size(100L)
                .content(new ByteArrayInputStream("test".getBytes()))
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeEnhancementSboms(null, ENHANCEMENT_ID, files)
        );
        assertEquals("generationId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testStoreEnhancementSboms_NullEnhancementId() {
        // Given
        SbomFile file = SbomFile.builder()
                .filename("enhanced.json")
                .contentType("application/json")
                .size(100L)
                .content(new ByteArrayInputStream("test".getBytes()))
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeEnhancementSboms(GENERATION_ID, null, files)
        );
        assertEquals("enhancementId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testStoreEnhancementSboms_EmptyEnhancementId() {
        // Given
        SbomFile file = SbomFile.builder()
                .filename("enhanced.json")
                .contentType("application/json")
                .size(100L)
                .content(new ByteArrayInputStream("test".getBytes()))
                .build();
        List<SbomFile> files = Arrays.asList(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeEnhancementSboms(GENERATION_ID, "  ", files)
        );
        assertEquals("enhancementId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void testGetFileContent_Success() {
        // Given
        String storageKey = "gen-123/bom.json";
        InputStream expectedStream = new ByteArrayInputStream("file content".getBytes());
        when(objectStorage.download(storageKey)).thenReturn(expectedStream);

        // When
        InputStream result = storageService.getFileContent(storageKey);

        // Then
        assertNotNull(result);
        assertEquals(expectedStream, result);
        verify(objectStorage).download(storageKey);
    }

    @Test
    void testGetFileContent_NullStorageKey() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.getFileContent(null)
        );
        assertEquals("storageKey cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).download(anyString());
    }

    @Test
    void testGetFileContent_EmptyStorageKey() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.getFileContent("  ")
        );
        assertEquals("storageKey cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).download(anyString());
    }

    @Test
    void testStoreGenerationSboms_PartialFailure() {
        // Given
        InputStream content1 = new ByteArrayInputStream("content1".getBytes());
        InputStream content2 = new ByteArrayInputStream("content2".getBytes());
        
        SbomFile file1 = SbomFile.builder()
                .filename("bom1.json")
                .contentType("application/json")
                .size(100L)
                .content(content1)
                .build();
        
        SbomFile file2 = SbomFile.builder()
                .filename("bom2.json")
                .contentType("application/json")
                .size(200L)
                .content(content2)
                .build();
        
        List<SbomFile> files = Arrays.asList(file1, file2);

        // First upload succeeds, second fails
        doNothing().when(objectStorage).upload(eq("gen-123/bom1.json"), any(), anyLong(), anyString());
        doThrow(new RuntimeException("Upload failed"))
                .when(objectStorage)
                .upload(eq("gen-123/bom2.json"), any(), anyLong(), anyString());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, files)
        );
        assertTrue(exception.getMessage().contains("Failed to upload file 'bom2.json'"));
        
        // Verify first file was uploaded before failure
        verify(objectStorage).upload(eq("gen-123/bom1.json"), eq(content1), eq(100L), eq("application/json"));
        verify(objectStorage).upload(eq("gen-123/bom2.json"), eq(content2), eq(200L), eq("application/json"));
    }
}
