package org.jboss.sbomer.manifest.storage.service.core.port.spi;

import java.io.InputStream;

public interface ObjectStorage {
    void upload(String key, InputStream content, long contentLength, String contentType);
    
    /**
     * Returns the raw stream from the storage provider.
     */
    InputStream download(String key);
}