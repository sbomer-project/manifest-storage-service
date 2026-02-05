package org.jboss.sbomer.manifest.storage.service.adapter.out;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static org.jboss.sbomer.manifest.storage.service.adapter.out.MinioTestResource.ACCESS_KEY_ID;
import static org.jboss.sbomer.manifest.storage.service.adapter.out.MinioTestResource.BUCKET_NAME;
import static org.jboss.sbomer.manifest.storage.service.adapter.out.MinioTestResource.PATH_STYLE_ACCESS;
import static org.jboss.sbomer.manifest.storage.service.adapter.out.MinioTestResource.REGION;
import static org.jboss.sbomer.manifest.storage.service.adapter.out.MinioTestResource.SECRET_ACCESS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static software.amazon.awssdk.auth.credentials.AwsBasicCredentials.*;
import static software.amazon.awssdk.core.retry.RetryMode.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.interceptor.Context.ModifyHttpResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpResponse;
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
    void testUploadAndDownload() throws IOException {
        String key = "foobar/file.txt";
        byte[] originalBytes = "456".getBytes();
        adapter.upload(key, new ByteArrayInputStream(originalBytes),
                       originalBytes.length, CONTENT_TYPE);
        try (InputStream downloaded = adapter.download(key)) {
            byte[] downloadedBytes = downloaded.readAllBytes();
            assertArrayEquals(originalBytes, downloadedBytes);
        }
    }

    @Test
    void testUploadAndDownloadLargeContent() throws IOException {
        String key = "foobar/large.txt";
        byte[] originalBytes = new byte[ONE_MB];
        new Random().nextBytes(originalBytes);
        adapter.upload(key, new ByteArrayInputStream(originalBytes),
                       originalBytes.length, CONTENT_TYPE);
        try (InputStream downloaded = adapter.download(key)) {
            byte[] downloadedBytes = downloaded.readAllBytes();
            assertArrayEquals(originalBytes, downloadedBytes);
        }
    }

    @Test
    void testDownloadNonExistentFile() {
        assertThrows(StorageFileNotFoundException.class, () ->
            adapter.download("foobar/non-existent.txt")
        );
    }

    @Test
    void testOverwriteExistingFile() throws IOException {
        String key = "foobar/overwrite.txt";
        byte[] firstBytes = "first".getBytes();
        byte[] secondBytes = "second".getBytes();
        adapter.upload(key, new ByteArrayInputStream(firstBytes),
                       firstBytes.length, CONTENT_TYPE);
        adapter.upload(key, new ByteArrayInputStream(secondBytes),
                       secondBytes.length, CONTENT_TYPE);
        try (InputStream downloaded = adapter.download(key)) {
            byte[] downloadedBytes = downloaded.readAllBytes();
            assertArrayEquals(secondBytes, downloadedBytes);
        }
    }

    @Test
    void testUploadAndDownloadEmptyFile() throws IOException {
        String key = "foobar/empty.txt";
        byte[] emptyBytes = new byte[0];
        adapter.upload(key, new ByteArrayInputStream(emptyBytes),
                       0L, CONTENT_TYPE);
        try (InputStream downloaded = adapter.download(key)) {
            byte[] downloadedBytes = downloaded.readAllBytes();
            assertArrayEquals(emptyBytes, downloadedBytes);
            assertEquals(0, downloadedBytes.length);
        }
    }

    /**
     * Verifies fix for AWS SDK retry scenario with non-markable streams.
     *
     * Production scenario:
     *  1. AWS SDK starts uploading a file
     *  2. Network failure/timeout occurs during upload
     *  3. AWS SDK detects retryable exception and retries internally
     * And either:
     *  4. With byte array buffering, retry succeeds (byte array can be reused)
     * Or:
     *  4. Without byte array buffering, retry fails (InputStream already consumed)
     *
     * The fix:
     *  S3StorageAdapter.upload() buffers InputStream to byte array, enabling AWS
     *  SDK's built-in retry mechanism to work with non-markable streams.
     *
     * @throws IOException if stream operations fail
     */
    @Test
    void testUploadNonMarkableStreamWithRetry() throws IOException {
        // Track attempt count to verify AWS SDK actually retries
        AtomicInteger attemptCount = new AtomicInteger(0);
        // Create client with retry enabled and interceptor that simulates retryable I/O error
        try (S3Client retryingClient = S3Client.builder()
                .endpointOverride(client.serviceClientConfiguration().endpointOverride().orElseThrow())
                .credentialsProvider(StaticCredentialsProvider.create(
                        create(ACCESS_KEY_ID, SECRET_ACCESS_KEY)))
                .region(REGION)
                .forcePathStyle(PATH_STYLE_ACCESS)
                .overrideConfiguration(config -> config
                        .addExecutionInterceptor(new ExecutionInterceptor() {
                            @Override
                            public SdkHttpResponse modifyHttpResponse(
                                    ModifyHttpResponse context,
                                    ExecutionAttributes executionAttributes) {
                                if (attemptCount.incrementAndGet() == 1) {
                                    // Return 503 Service Unavailable on first attempt, which is retryable
                                    return SdkHttpResponse.builder()
                                            .statusCode(SERVICE_UNAVAILABLE.getStatusCode())
                                            .build();
                                }
                                // Subsequent attempts get real response
                                return context.httpResponse();
                            }
                        })
                        .retryStrategy(STANDARD))
                .build()) {
            S3StorageAdapter retryAdapter = new S3StorageAdapter(retryingClient, BUCKET_NAME);
            String key = "foobar/non-markable-retry.txt";
            // SequenceInputStream naturally doesn't support mark/reset (like FileInputStream)
            byte[] originalContent = "123456".getBytes();
            InputStream nonMarkableStream = new SequenceInputStream(
                    new ByteArrayInputStream(originalContent),
                    new ByteArrayInputStream(new byte[0]));
            assertFalse(nonMarkableStream.markSupported());
            assertThrows(IOException.class, nonMarkableStream::reset);
            retryAdapter.upload(key, nonMarkableStream, originalContent.length, CONTENT_TYPE);
            assertEquals(2, attemptCount.get());
            try (InputStream downloaded = adapter.download(key)) {
                byte[] downloadedContent = downloaded.readAllBytes();
                assertArrayEquals(originalContent, downloadedContent);
            }
        }
    }
}