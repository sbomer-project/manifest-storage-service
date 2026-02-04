package org.jboss.sbomer.manifest.storage.service.adapter.out;

import static org.jboss.sbomer.manifest.storage.service.adapter.out.MinioTestResource.BUCKET_NAME;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Random;

import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Integration tests S3StorageAdapter.
 * Tests upload and download operations.
 * Spins up a real MinIO instance for end-to-end testing.
 */
@QuarkusTest
@QuarkusTestResource(MinioTestResource.class)
class S3StorageAdapterIntegrationTest {

    private static final String CONTENT_TYPE = "text/plain";
    private static final int ONE_MB = 1048576;

    @Inject
    S3StorageAdapter adapter;

    @Inject
    S3Client client;

    @BeforeEach
    void createBucket() {
        // Create test bucket if it doesn't exist
        try {
            client.createBucket(CreateBucketRequest.builder()
                .bucket(BUCKET_NAME)
                .build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            // Bucket already exists, ignore
        }
    }

    @Test
    void testUploadAndDownload() throws Exception {
        byte[] originalBytes = "456".getBytes();
        String key = "foobar/file.txt";
        adapter.upload(key, new ByteArrayInputStream(originalBytes),
                       originalBytes.length, CONTENT_TYPE);
        InputStream downloaded = adapter.download(key);
        byte[] downloadedBytes = downloaded.readAllBytes();
        assertArrayEquals(originalBytes, downloadedBytes);
    }

    @Test
    void testUploadAndDownloadLargeContent() throws Exception {
        byte[] originalBytes = new byte[ONE_MB];
        new Random().nextBytes(originalBytes);
        String key = "foobar/large.txt";
        adapter.upload(key, new ByteArrayInputStream(originalBytes),
                       originalBytes.length, CONTENT_TYPE);
        InputStream downloaded = adapter.download(key);
        byte[] downloadedBytes = downloaded.readAllBytes();
        assertArrayEquals(originalBytes, downloadedBytes);
    }

    @Test
    void testDownloadNonExistentFile() {
        assertThrows(StorageFileNotFoundException.class, () ->
            adapter.download("non-existent.txt")
        );
    }

    @Test
    void testOverwriteExistingFile() throws Exception {
        String key = "foobar/overwrite.txt";
        byte[] firstContent = "first".getBytes();
        byte[] secondContent = "second".getBytes();
        adapter.upload(key, new ByteArrayInputStream(firstContent),
                       firstContent.length, CONTENT_TYPE);
        adapter.upload(key, new ByteArrayInputStream(secondContent),
                       secondContent.length, CONTENT_TYPE);
        byte[] downloaded = adapter.download(key).readAllBytes();
        assertArrayEquals(secondContent, downloaded);
    }

    @Test
    void testUploadAndDownloadEmptyFile() throws Exception {
        byte[] emptyBytes = new byte[0];
        String key = "foobar/empty.txt";
        adapter.upload(key, new ByteArrayInputStream(emptyBytes),
                       0L, CONTENT_TYPE);
        byte[] downloaded = adapter.download(key).readAllBytes();
        assertArrayEquals(emptyBytes, downloaded);
        assertEquals(0, downloaded.length);
    }
}