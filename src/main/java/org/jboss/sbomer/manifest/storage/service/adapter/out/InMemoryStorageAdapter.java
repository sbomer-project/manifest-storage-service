package org.jboss.sbomer.manifest.storage.service.adapter.out;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.sbomer.manifest.storage.service.core.port.spi.ObjectStorage;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * A temporary, in-memory implementation of the ObjectStorage port.
 * Data is lost when the application restarts.
 */
@ApplicationScoped
@Slf4j
public class InMemoryStorageAdapter implements ObjectStorage {

    // The temporary "Bucket" simulation
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        try {
            // Read the stream into memory
            byte[] data = content.readAllBytes();
            
            storage.put(key, data);
            log.info("Stored file in memory: {} ({} bytes)", key, data.length);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream for memory storage", e);
        }
    }

    @Override
    public InputStream download(String key) {
        byte[] data = storage.get(key);
        
        if (data == null) {
            log.warn("File not found in memory: {}", key);
            throw new RuntimeException("File not found: " + key); // Triggers 404 in Resource
        }

        log.debug("Retrieving file from memory: {}", key);
        return new ByteArrayInputStream(data);
    }
}
