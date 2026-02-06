package org.jboss.sbomer.manifest.storage.service.adapter.out;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageAccessException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageFileNotFoundException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageKeyInvalidException;
import org.jboss.sbomer.manifest.storage.service.adapter.out.exception.StorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Unit tests for S3StorageAdapter.
 * Tests upload and download operations.
 */
@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    private static final String BUCKET_NAME = "test-storage";
    private static final String CONTENT_TYPE = "text/plain";

    @Mock
    S3Client client;

    S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new S3StorageAdapter(client, BUCKET_NAME);
    }

    @Test
    void testUploadSuccess() {
        String key = "foo/file.txt";
        byte[] bytes = "123".getBytes();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());
        adapter.upload(key, new ByteArrayInputStream(bytes), bytes.length, CONTENT_TYPE);
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals(key, capturedRequest.key());
        assertEquals(bytes.length, capturedRequest.contentLength());
        assertEquals(CONTENT_TYPE, capturedRequest.contentType());
    }

    @Test
    void testUploadNullKey() {
        assertThrows(StorageKeyInvalidException.class, () ->
            adapter.upload(null, new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testUploadEmptyKey() {
        assertThrows(StorageKeyInvalidException.class, () ->
            adapter.upload("  ", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testUploadPathTraversal() {
        assertThrows(StorageKeyInvalidException.class, () ->
            adapter.upload("../foo", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testUploadNoSuchBucket() {
        String bucketName = "non-existent";
        S3StorageAdapter noBucketAdapter = new S3StorageAdapter(client, bucketName);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());
        StorageException ex = assertThrows(StorageException.class, () ->
            noBucketAdapter.upload("foo/non-existent.txt", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
        assertTrue(ex.getMessage().contains(bucketName));
    }

    @Test
    void testUploadForbidden() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .statusCode(FORBIDDEN.getStatusCode())
            .message("Access Denied")
            .build();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(s3Exception);
        assertThrows(StorageAccessException.class, () ->
            adapter.upload("foo/forbidden.txt", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testUploadRateLimited() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .statusCode(TOO_MANY_REQUESTS.getStatusCode())
            .message("Too Many Requests")
            .build();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(s3Exception);
        StorageUnavailableException ex = assertThrows(StorageUnavailableException.class, () ->
            adapter.upload("foo/rate-limited.txt", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    void testUploadServiceUnavailable() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .statusCode(SERVICE_UNAVAILABLE.getStatusCode())
            .message("Service Unavailable")
            .build();
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(s3Exception);
        assertThrows(StorageUnavailableException.class, () ->
            adapter.upload("foo/service-unavailable.txt", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testUploadConnectionError() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(SdkClientException.create("Connection error"));
        assertThrows(StorageUnavailableException.class, () ->
            adapter.upload("foo/connection-error.txt", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testUploadUnexpectedError() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(new RuntimeException("Unexpected error"));
        assertThrows(StorageException.class, () ->
            adapter.upload("foo/unexpected-error.txt", new ByteArrayInputStream(new byte[0]), 0, CONTENT_TYPE)
        );
    }

    @Test
    void testDownloadSuccess() {
        String key = "bar/file.txt";
        ResponseInputStream<GetObjectResponse> mockResponse = mock(ResponseInputStream.class);
        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
            .contentLength(3L)
            .build();
        when(mockResponse.response()).thenReturn(getObjectResponse);
        when(client.getObject(any(GetObjectRequest.class)))
            .thenReturn(mockResponse);
        InputStream result = adapter.download(key);
        assertNotNull(result);
        assertEquals(mockResponse, result);
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(requestCaptor.capture());
        GetObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals(key, capturedRequest.key());
    }

    @Test
    void testDownloadNullKey() {
        assertThrows(StorageKeyInvalidException.class, () ->
            adapter.download(null)
        );
    }

    @Test
    void testDownloadEmptyKey() {
        assertThrows(StorageKeyInvalidException.class, () ->
            adapter.download("  ")
        );
    }

    @Test
    void testDownloadPathTraversal() {
        assertThrows(StorageKeyInvalidException.class, () ->
            adapter.download("../foo")
        );
    }

    @Test
    void testDownloadNoSuchKey() {
        String key = "bar/non-existent.txt";
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().message("Key not found").build());
        StorageFileNotFoundException ex = assertThrows(StorageFileNotFoundException.class, () ->
            adapter.download(key)
        );
        assertTrue(ex.getMessage().contains(key));
    }

    @Test
    void testDownloadNoSuchBucket() {
        String key = "bar/non-existent.txt";
        String bucketName = "non-existent";
        S3StorageAdapter noBucketAdapter = new S3StorageAdapter(client, bucketName);
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());
        StorageException ex = assertThrows(StorageException.class, () ->
                noBucketAdapter.download(key)
        );
        assertTrue(ex.getMessage().contains(bucketName));
    }

    @Test
    void testDownloadForbidden() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .statusCode(FORBIDDEN.getStatusCode())
            .message("Access Denied")
            .build();
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(s3Exception);
        assertThrows(StorageAccessException.class, () ->
            adapter.download("bar/forbidden.txt")
        );
    }

    @Test
    void testDownloadRateLimited() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .statusCode(TOO_MANY_REQUESTS.getStatusCode())
            .message("Too Many Requests")
            .build();
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(s3Exception);
        assertThrows(StorageUnavailableException.class, () ->
            adapter.download("bar/rate-limited.txt")
        );
    }

    @Test
    void testDownloadServiceUnavailable() {
        S3Exception s3Exception = (S3Exception) S3Exception.builder()
            .statusCode(SERVICE_UNAVAILABLE.getStatusCode())
            .message("Service Unavailable")
            .build();
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(s3Exception);
        assertThrows(StorageUnavailableException.class, () ->
            adapter.download("bar/service-unavailable.txt")
        );
    }

    @Test
    void testDownloadConnectionError() {
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(SdkClientException.create("Connection error"));
        assertThrows(StorageUnavailableException.class, () ->
            adapter.download("bar/connection-error.txt")
        );
    }

    @Test
    void testDownloadUnexpectedError() {
        when(client.getObject(any(GetObjectRequest.class)))
            .thenThrow(new RuntimeException("Unexpected error"));
        assertThrows(StorageException.class, () ->
            adapter.download("bar/unexpected-error.txt")
        );
    }
}