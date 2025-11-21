package org.jboss.sbomer.manifest.storage.service.core.domain.model;

import java.io.InputStream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SbomFile {
    private String filename;
    private String contentType;
    private InputStream content;
    private long size;
}
