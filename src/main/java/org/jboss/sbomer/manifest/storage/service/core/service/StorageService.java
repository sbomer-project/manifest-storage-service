package org.jboss.sbomer.manifest.storage.service.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;
import org.jboss.sbomer.manifest.storage.service.core.port.spi.ObjectStorage;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing SBOM file storage operations.
 * Provides atomic batch upload functionality with proper validation and error handling.
 */
@ApplicationScoped
@Slf4j
public class StorageService implements StorageAdministration {

    private final ObjectStorage objectStorage;
    private final String publicApiUrl;

    @Inject
    public StorageService(
            ObjectStorage objectStorage,
            @ConfigProperty(name = "sbomer.storage.public-api-url") String publicApiUrl) {
        this.objectStorage = objectStorage;
        this.publicApiUrl = publicApiUrl;
    }

    @WithSpan
    @Override
    public Map<String, String> storeGenerationSboms(
            @SpanAttribute("generation.id") String generationId,
            List<SbomFile> files) {
        validateInput(generationId, "generationId");
        validateFiles(files);
        log.info("Storing {} generation SBOM(s) for generation: {}", files.size(), generationId);
        return uploadBatch(generationId, files);
    }

    @WithSpan
    @Override
    public Map<String, String> storeEnhancementSboms(
            @SpanAttribute("generation.id") String generationId,
            @SpanAttribute("enhancement.id") String enhancementId,
            List<SbomFile> files) {
        validateInput(generationId, "generationId");
        validateInput(enhancementId, "enhancementId");
        validateFiles(files);
        
        String prefix = String.format("%s/%s", generationId, enhancementId);
        log.info("Storing {} enhancement SBOM(s) for generation: {}, enhancement: {}",
                files.size(), generationId, enhancementId);
        return uploadBatch(prefix, files);
    }

    @Override
    public InputStream getFileContent(String storageKey) {
        validateInput(storageKey, "storageKey");
        log.debug("Retrieving file content for key: {}", storageKey);
        
        // Return the stream directly - caller is responsible for closing it
        // In this case, JAX-RS will handle closing the stream after response is sent
        return objectStorage.download(storageKey);
    }

    /**
     * Uploads a batch of files with fail-fast behavior.
     *
     * If any file fails during upload, the operation stops immediately and throws an exception.
     * However, files that were successfully uploaded before the failure remain in storage
     * (no automatic rollback is performed).
     *
     * Properly manages InputStream resources using try-with-resources pattern.
     * Each file's InputStream is opened, used, and closed within the same iteration
     * to ensure proper resource management.
     *
     * @param folderPrefix the storage folder prefix (e.g., "gen-123" or "gen-123/enh-456")
     * @param files list of files to upload
     * @return map of filename to permanent download URL for successfully uploaded files
     * @throws RuntimeException if any file fails to open or upload
     */
    private Map<String, String> uploadBatch(String folderPrefix, List<SbomFile> files) {
        Map<String, String> resultUrls = new HashMap<>();

        for (SbomFile file : files) {
            String storageKey = String.format("%s/%s", folderPrefix, file.getFilename());
            
            // Use try-with-resources to ensure InputStream is always closed
            // The stream is opened, used, and closed within this block
            try (InputStream content = Files.newInputStream(file.getFilePath())) {
                log.debug("Uploading file: {} to key: {}", file.getFilename(), storageKey);
                objectStorage.upload(storageKey, content, file.getSize(), file.getContentType());
                
                String permanentUrl = String.format("%s/api/v1/storage/content/%s", publicApiUrl, storageKey);
                resultUrls.put(file.getFilename(), permanentUrl);
                
            } catch (IOException e) {
                log.error("Failed to open file: {} for upload", file.getFilename(), e);
                throw new RuntimeException(
                        String.format("Failed to open file '%s': %s", file.getFilename(), e.getMessage()),
                        e);
            } catch (Exception e) {
                log.error("Upload failed for file: {} at key: {}. Aborting batch.",
                        file.getFilename(), storageKey, e);
                throw new RuntimeException(
                        String.format("Failed to upload file '%s': %s", file.getFilename(), e.getMessage()),
                        e);
            }
        }
        
        log.info("Successfully uploaded {} file(s) to folder: {}", files.size(), folderPrefix);
        return resultUrls;
    }

    private void validateInput(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
    }

    private void validateFiles(List<SbomFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be null or empty");
        }
    }
}

