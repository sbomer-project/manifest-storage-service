package org.jboss.sbomer.manifest.storage.service.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.spi.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService")
class StorageServiceTest {

    @Mock
    ObjectStorage objectStorage;

    @TempDir
    Path tempDir;

    private static final String PUBLIC_API_URL = "https://api.example.com";
    private static final String GENERATION_ID = "gen-123";
    private static final String ENHANCEMENT_ID = "enh-456";

    /**
     * Helper method to create a temporary file with content
     */
    private Path createTempFile(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    /**
     * Helper method to create an SbomFile with a temporary file
     */
    private SbomFile createSbomFile(String filename, String content, String contentType) throws IOException {
        Path filePath = createTempFile(filename, content);
        return SbomFile.builder()
                .filename(filename)
                .contentType(contentType)
                .filePath(filePath)
                .size(Files.size(filePath))
                .build();
    }

    @Test
    @DisplayName("should successfully store a single generation SBOM file")
    void testStoreGenerationSboms_Success() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("bom.json", "test content", "application/json");
        List<SbomFile> files = List.of(file);

        // When
        Map<String, String> result = storageService.storeGenerationSboms(GENERATION_ID, files);

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size()),
                () -> assertTrue(result.containsKey("bom.json")),
                () -> assertEquals(PUBLIC_API_URL + "/api/v1/storage/content/gen-123/bom.json",
                        result.get("bom.json"))
        );

        verify(objectStorage).upload(
                eq("gen-123/bom.json"),
                any(InputStream.class),
                eq(file.getSize()),
                eq("application/json")
        );
    }

    @Test
    @DisplayName("should successfully store multiple generation SBOM files")
    void testStoreGenerationSboms_MultipleFiles() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file1 = createSbomFile("bom1.json", "content1", "application/json");
        SbomFile file2 = createSbomFile("bom2.json", "content2", "application/json");

        List<SbomFile> files = List.of(file1, file2);

        // When
        Map<String, String> result = storageService.storeGenerationSboms(GENERATION_ID, files);

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.containsKey("bom1.json")),
                () -> assertTrue(result.containsKey("bom2.json"))
        );

        verify(objectStorage).upload(
                eq("gen-123/bom1.json"),
                any(InputStream.class),
                eq(file1.getSize()),
                eq("application/json")
        );
        verify(objectStorage).upload(
                eq("gen-123/bom2.json"),
                any(InputStream.class),
                eq(file2.getSize()),
                eq("application/json")
        );
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when generationId is null")
    void testStoreGenerationSboms_NullGenerationId() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("bom.json", "test", "application/json");
        List<SbomFile> files = List.of(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms(null, files)
        );
        assertEquals("generationId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when generationId is empty")
    void testStoreGenerationSboms_EmptyGenerationId() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("bom.json", "test", "application/json");
        List<SbomFile> files = List.of(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms("  ", files)
        );
        assertEquals("generationId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when files list is null")
    void testStoreGenerationSboms_NullFiles() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, null)
        );
        assertEquals("Files list cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when files list is empty")
    void testStoreGenerationSboms_EmptyFiles() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, List.of())
        );
        assertEquals("Files list cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw RuntimeException when upload fails")
    void testStoreGenerationSboms_UploadFailure() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("bom.json", "test content", "application/json");
        List<SbomFile> files = List.of(file);

        doThrow(new RuntimeException("S3 error"))
                .when(objectStorage)
                .upload(anyString(), any(InputStream.class), anyLong(), anyString());

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, files)
        );
        assertAll(
                () -> assertTrue(exception.getMessage().contains("Failed to upload file 'bom.json'")),
                () -> assertTrue(exception.getCause().getMessage().contains("S3 error"))
        );
    }

    @Test
    @DisplayName("should throw RuntimeException when file does not exist")
    void testStoreGenerationSboms_FileNotFound() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        Path nonExistentPath = tempDir.resolve("non-existent.json");
        SbomFile file = SbomFile.builder()
                .filename("non-existent.json")
                .contentType("application/json")
                .filePath(nonExistentPath)
                .size(100L)
                .build();
        List<SbomFile> files = List.of(file);

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, files)
        );
        assertTrue(exception.getMessage().contains("Failed to open file 'non-existent.json'"));
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should fail entire batch when second file upload fails")
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testStoreGenerationSboms_PartialFailure() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file1 = createSbomFile("bom1.json", "content1", "application/json");
        SbomFile file2 = createSbomFile("bom2.json", "content2", "application/json");

        List<SbomFile> files = List.of(file1, file2);

        // First upload succeeds (default behavior), second fails
        doThrow(new RuntimeException("Upload failed"))
                .when(objectStorage)
                .upload(eq("gen-123/bom2.json"), any(InputStream.class), eq(file2.getSize()), eq("application/json"));

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageService.storeGenerationSboms(GENERATION_ID, files)
        );
        assertTrue(exception.getMessage().contains("Failed to upload file 'bom2.json'"));

        // Verify first file was uploaded before failure
        verify(objectStorage).upload(eq("gen-123/bom1.json"), any(InputStream.class), eq(file1.getSize()),
                eq("application/json"));
        verify(objectStorage).upload(eq("gen-123/bom2.json"), any(InputStream.class), eq(file2.getSize()),
                eq("application/json"));
    }

    @Test
    @DisplayName("should successfully store a single enhancement SBOM file")
    void testStoreEnhancementSboms_Success() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("enhanced.json", "test content", "application/json");
        List<SbomFile> files = List.of(file);

        // When
        Map<String, String> result = storageService.storeEnhancementSboms(GENERATION_ID, ENHANCEMENT_ID, files);

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(1, result.size()),
                () -> assertTrue(result.containsKey("enhanced.json")),
                () -> assertEquals(
                        PUBLIC_API_URL + "/api/v1/storage/content/gen-123/enh-456/enhanced.json",
                        result.get("enhanced.json"))
        );

        verify(objectStorage).upload(
                eq("gen-123/enh-456/enhanced.json"),
                any(InputStream.class),
                eq(file.getSize()),
                eq("application/json")
        );
    }

    @Test
    @DisplayName("should successfully store multiple enhancement SBOM files")
    void testStoreEnhancementSboms_MultipleFiles() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file1 = createSbomFile("enhanced1.json", "content1", "application/json");
        SbomFile file2 = createSbomFile("enhanced2.json", "content2", "application/json");

        List<SbomFile> files = List.of(file1, file2);

        // When
        Map<String, String> result = storageService.storeEnhancementSboms(GENERATION_ID, ENHANCEMENT_ID, files);

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(2, result.size()),
                () -> assertTrue(result.containsKey("enhanced1.json")),
                () -> assertTrue(result.containsKey("enhanced2.json")),
                () -> assertEquals(
                        PUBLIC_API_URL + "/api/v1/storage/content/gen-123/enh-456/enhanced1.json",
                        result.get("enhanced1.json")),
                () -> assertEquals(
                        PUBLIC_API_URL + "/api/v1/storage/content/gen-123/enh-456/enhanced2.json",
                        result.get("enhanced2.json"))
        );

        verify(objectStorage).upload(
                eq("gen-123/enh-456/enhanced1.json"),
                any(InputStream.class),
                eq(file1.getSize()),
                eq("application/json")
        );
        verify(objectStorage).upload(
                eq("gen-123/enh-456/enhanced2.json"),
                any(InputStream.class),
                eq(file2.getSize()),
                eq("application/json")
        );
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when generationId is null for enhancement")
    void testStoreEnhancementSboms_NullGenerationId() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("enhanced.json", "test", "application/json");
        List<SbomFile> files = List.of(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeEnhancementSboms(null, ENHANCEMENT_ID, files)
        );
        assertEquals("generationId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when enhancementId is null")
    void testStoreEnhancementSboms_NullEnhancementId() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("enhanced.json", "test", "application/json");
        List<SbomFile> files = List.of(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeEnhancementSboms(GENERATION_ID, null, files)
        );
        assertEquals("enhancementId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when enhancementId is empty")
    void testStoreEnhancementSboms_EmptyEnhancementId() throws IOException {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        SbomFile file = createSbomFile("enhanced.json", "test", "application/json");
        List<SbomFile> files = List.of(file);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.storeEnhancementSboms(GENERATION_ID, "  ", files)
        );
        assertEquals("enhancementId cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should throw RuntimeException when enhancement file does not exist")
    void testStoreEnhancementSboms_FileOpenFailure() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        Path nonExistentPath = tempDir.resolve("missing.json");
        SbomFile file = SbomFile.builder()
                .filename("missing.json")
                .contentType("application/json")
                .filePath(nonExistentPath)
                .size(100L)
                .build();
        List<SbomFile> files = List.of(file);

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> storageService.storeEnhancementSboms(GENERATION_ID, ENHANCEMENT_ID, files)
        );
        assertTrue(exception.getMessage().contains("Failed to open file 'missing.json'"));
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should successfully retrieve file content")
    void testGetFileContent_Success() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);
        String storageKey = "gen-123/bom.json";
        InputStream expectedStream = mock(InputStream.class);
        when(objectStorage.download(storageKey)).thenReturn(expectedStream);

        // When
        InputStream result = storageService.getFileContent(storageKey);

        // Then
        assertAll(
                () -> assertNotNull(result),
                () -> assertEquals(expectedStream, result)
        );
        verify(objectStorage).download(storageKey);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when storageKey is null")
    void testGetFileContent_NullStorageKey() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.getFileContent(null)
        );
        assertEquals("storageKey cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).download(anyString());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when storageKey is empty")
    void testGetFileContent_EmptyStorageKey() {
        // Given
        StorageService storageService = new StorageService(objectStorage, PUBLIC_API_URL);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.getFileContent("  ")
        );
        assertEquals("storageKey cannot be null or empty", exception.getMessage());
        verify(objectStorage, never()).download(anyString());
    }
}

