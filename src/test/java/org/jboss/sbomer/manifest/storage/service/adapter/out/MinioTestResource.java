package org.jboss.sbomer.manifest.storage.service.adapter.out;

import java.util.Map;

import org.testcontainers.containers.MinIOContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Test resource that provides a MinIO container for integration tests.
 */
public class MinioTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String BUCKET_NAME = "test";
    private static final String MINIO_IMAGE = "minio/minio:latest";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    private MinIOContainer minio;

    @Override
    public Map<String, String> start() {
        minio = new MinIOContainer(MINIO_IMAGE)
            .withUserName(ACCESS_KEY)
            .withPassword(SECRET_KEY);
        minio.start();
        return Map.ofEntries(
            Map.entry("quarkus.s3.endpoint-override", minio.getS3URL()),
            Map.entry("quarkus.s3.aws.credentials.static-provider.access-key-id", ACCESS_KEY),
            Map.entry("quarkus.s3.aws.credentials.static-provider.secret-access-key", SECRET_KEY),
            Map.entry("quarkus.s3.aws.region", "us-east-1"),
            Map.entry("quarkus.s3.path-style-access", "true"),
            Map.entry("sbomer.storage.s3.bucket", BUCKET_NAME),
            Map.entry("kafka.bootstrap.servers", "localhost:9092"),
            Map.entry("kafka.apicurio.registry.auto-register", "false"),
            Map.entry("kafka.apicurio.registry.url", "")
        );
    }

    @Override
    public void stop() {
        if (minio != null) {
            minio.stop();
        }
    }
}