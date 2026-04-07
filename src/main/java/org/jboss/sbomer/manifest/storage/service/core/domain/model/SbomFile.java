package org.jboss.sbomer.manifest.storage.service.core.domain.model;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Getter;

/**
 * Domain model representing an SBOM file.
 * Stores file metadata and path reference.
 *
 */
@Getter
@Builder
public class SbomFile {
    private String filename;
    private String contentType;
    private Path filePath;
    private long size;
}
