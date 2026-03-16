package org.jboss.sbomer.manifest.storage.service.core.service;

import java.io.InputStream;
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

    @Inject
    ObjectStorage objectStorage;

    @ConfigProperty(name = "sbomer.storage.public-api-url")
    String publicApiUrl;

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
        return objectStorage.download(storageKey);
    }

    /**
     * Uploads a batch of files atomically. If any file fails, the entire batch fails.
     */
    private Map<String, String> uploadBatch(String folderPrefix, List<SbomFile> files) {
        Map<String, String> resultUrls = new HashMap<>();

        for (SbomFile file : files) {
            String storageKey = String.format("%s/%s", folderPrefix, file.getFilename());
            
            try {
                log.debug("Uploading file: {} to key: {}", file.getFilename(), storageKey);
                objectStorage.upload(storageKey, file.getContent(), file.getSize(), file.getContentType());
                
                String permanentUrl = String.format("%s/api/v1/storage/content/%s", publicApiUrl, storageKey);
                resultUrls.put(file.getFilename(), permanentUrl);
                
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
