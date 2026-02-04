package org.jboss.sbomer.manifest.storage.service.adapter.out;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

import java.io.InputStream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageAccessException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageKeyInvalidException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageUnavailableException;
import org.jboss.sbomer.manifest.storage.service.core.port.spi.ObjectStorage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 compatible storage adapter implementation using AWS SDK.
 * Provides upload and download operations for object storage backends.
 */
@ApplicationScoped
@Slf4j
public class S3StorageAdapter implements ObjectStorage {

    @Inject
    S3Client client;

    @ConfigProperty(name = "sbomer.storage.s3.bucket")
    String bucketName;

    void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    /**
     * Uploads content to S3 compatible storage.
     * @param key object key (path) in bucket, must not be null or contain '..'
     * @param content content to upload as an InputStream
     * @param contentLength size of content in bytes
     * @param contentType MIME type of content (e.g., 'application/json')
     * @throws StorageKeyInvalidException if key is null, empty, or contains path traversal patterns
     * @throws StorageException if bucket doesn't exist or unexpected error occurs
     * @throws StorageAccessException if access is denied (HTTP 403)
     * @throws StorageUnavailableException if storage is unavailable or rate limited
     */
    @Override
    public void upload(String key, InputStream content, long contentLength, String contentType) {
        validateKey(key);
        try {
            log.info("Uploading to S3 bucket '{}': {}", bucketName, key);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentLength(contentLength)
                    .contentType(contentType)
                    .build();
            client.putObject(request, RequestBody.fromInputStream(content, contentLength));
            log.info("Uploaded to S3 bucket '{}': {} ({} bytes)", bucketName, key, contentLength);
        } catch (Exception e) {
            throw handleException(e, key);
        }
    }

    /**
     * Downloads content from S3 compatible storage.
     * @param key object key (path) in bucket, must not be null or contain ".."
     * @return an InputStream containing object content (caller must close it)
     * @throws StorageKeyInvalidException if key is null, empty, or contains path traversal patterns
     * @throws StorageFileNotFoundException if object doesn't exist at specified key
     * @throws StorageException if bucket doesn't exist or an unexpected error occurs
     * @throws StorageAccessException if access is denied (HTTP 403)
     * @throws StorageUnavailableException if storage is unavailable or rate limited
     */
    @Override
    public InputStream download(String key) {
        validateKey(key);
        try {
            log.info("Downloading from S3 bucket '{}': {}", bucketName, key);
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            ResponseInputStream<GetObjectResponse> responseInputStream = client.getObject(request);
            log.info("Downloaded from S3 bucket '{}': {}", bucketName, key);
            return responseInputStream;
        } catch (NoSuchKeyException e) {
            throw new StorageFileNotFoundException("File not found: " + key, e);
        } catch (Exception e) {
            throw handleException(e, key);
        }
    }

    /**
     * Validates storage key is not null, empty, or contains path traversal.
     * @param key storage key to validate
     * @throws StorageKeyInvalidException if key is invalid
     */
    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new StorageKeyInvalidException(key, "Key cannot be empty");
        }
        if (key.contains("..")) {
            throw new StorageKeyInvalidException(key, "Path traversal not allowed");
        }
    }

    /**
     * Handles common exceptions and translates them to domain exceptions.
     * @param e exception to handle
     * @param key object key being accessed
     * @return appropriate domain exception
     */
    private RuntimeException handleException(Exception e, String key) {
        if (e instanceof NoSuchBucketException) {
            return new StorageException("Storage bucket not found: " + bucketName, e);
        } else if (e instanceof S3Exception s3Exception) {
            return handleS3Exception(s3Exception, key);
        } else if (e instanceof SdkClientException) {
            return new StorageUnavailableException("Unable to connect to storage bucket: " + bucketName, e);
        } else {
            return new StorageException("Unexpected error for: " + key, e);
        }
    }

    /**
     * Handles S3 exceptions and translates them to domain exceptions.
     * @param e S3 exception to handle
     * @param key object key being accessed
     * @return appropriate domain exception
     */
    private RuntimeException handleS3Exception(S3Exception e, String key) {
        int statusCode = e.statusCode();
        if (statusCode == FORBIDDEN.getStatusCode()) {
            return new StorageAccessException("Access denied to storage bucket: " + bucketName, e);
        } else if (statusCode == TOO_MANY_REQUESTS.getStatusCode()) {
            return new StorageUnavailableException("Storage rate limit exceeded", e);
        } else if (statusCode == SERVICE_UNAVAILABLE.getStatusCode()) {
            return new StorageUnavailableException("Storage unavailable", e);
        } else {
            return new StorageException("Storage error for: " + key, e);
        }
    }
}