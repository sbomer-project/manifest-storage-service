package org.jboss.sbomer.manifest.storage.service.adapter.out;

import static software.amazon.awssdk.regions.Region.US_EAST_1;

import java.util.Map;

import org.testcontainers.containers.MinIOContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import software.amazon.awssdk.regions.Region;

/**
 * Test resource that provides a MinIO container for integration tests.
 */
public class MinioTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String BUCKET_NAME = "test";
    public static final String ACCESS_KEY_ID = "minioadmin";
    public static final String SECRET_ACCESS_KEY = "minioadmin";
    public static final Region REGION = US_EAST_1;
    public static final boolean PATH_STYLE_ACCESS = true;
    private static final String MINIO_IMAGE = "minio/minio:latest";

    private MinIOContainer minio;

    @Override
    public Map<String, String> start() {
        minio = new MinIOContainer(MINIO_IMAGE)
            .withUserName(ACCESS_KEY_ID)
            .withPassword(SECRET_ACCESS_KEY);
        minio.start();
        return Map.ofEntries(
            Map.entry("quarkus.s3.endpoint-override", minio.getS3URL()),
            Map.entry("quarkus.s3.aws.credentials.static-provider.access-key-id", ACCESS_KEY_ID),
            Map.entry("quarkus.s3.aws.credentials.static-provider.secret-access-key", SECRET_ACCESS_KEY),
            Map.entry("quarkus.s3.aws.region", REGION.id()),
            Map.entry("quarkus.s3.path-style-access", String.valueOf(PATH_STYLE_ACCESS)),
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