package org.jboss.sbomer.manifest.storage.service.core.service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;
import org.jboss.sbomer.manifest.storage.service.core.port.spi.ObjectStorage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class StorageService implements StorageAdministration {

    @Inject
    ObjectStorage objectStorage;

    // the api url of this service component
    @ConfigProperty(name = "sbomer.storage.api-url")
    String publicApiUrl;

    @Override
    public Map<String, String> storeGenerationSboms(String generationId, List<SbomFile> files) {
        // generationId is the prefix
        return uploadBatch(generationId, files);
    }

    @Override
    public Map<String, String> storeEnhancementSboms(String generationId, String enhancementId, List<SbomFile> files) {
        // generationId/enhancementId is the prefix
        String prefix = String.format("%s/%s", generationId, enhancementId);
        return uploadBatch(prefix, files);
    }

    /**
     * Helper to handle the Batch Atomicity logic
     */
    private Map<String, String> uploadBatch(String folderPrefix, List<SbomFile> files) {
        log.info("Uploading {} files to folder: {}", files.size(), folderPrefix);

        Map<String, String> resultUrls = new HashMap<>();

        for (SbomFile file : files) {
            // Final Key: folderPrefix/filename
            String storageKey = String.format("%s/%s", folderPrefix, file.getFilename());

            try {
                objectStorage.upload(storageKey, file.getContent(), file.getSize(), file.getContentType());

                // Construct permanent URL
                String permUrl = String.format("%s/api/v1/storage/content/%s", publicApiUrl, storageKey);
                resultUrls.put(file.getFilename(), permUrl);

            } catch (Exception e) {
                log.error("Upload failed for file {}. Aborting batch.", file.getFilename(), e);
                // Atomic failure: Throw exception to ensure 500 Error and no partial state in DB
                throw new RuntimeException("Failed to upload file " + file.getFilename(), e);
            }
        }
        return resultUrls;
    }

    @Override
    public InputStream getFileContent(String storageKey) {
        return objectStorage.download(storageKey);
    }
}
