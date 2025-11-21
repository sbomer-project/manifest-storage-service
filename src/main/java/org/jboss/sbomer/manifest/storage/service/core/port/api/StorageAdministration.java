package org.jboss.sbomer.manifest.storage.service.core.port.api;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;

public interface StorageAdministration {

    /**
     * Stores files at the root of the generation folder.
     * Path: {generationId}/{filename}
     */
    Map<String, String> storeGenerationSboms(String generationId, List<SbomFile> files);

    /**
     * Stores files nested under the generation in an enhancement folder.
     * Path: {generationId}/{enhancementId}/{filename}
     */
    Map<String, String> storeEnhancementSboms(String generationId, String enhancementId, List<SbomFile> files);

    InputStream getFileContent(String storageKey);
}
