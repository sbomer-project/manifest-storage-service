# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Project Overview

The **Manifest Storage Service** is a Quarkus-based microservice within the SBOMer NextGen architecture, responsible for persistent storage and retrieval of SBOM (Software Bill of Materials) manifests. It provides a REST API abstraction layer over S3-compatible object storage with atomic batch upload capabilities.

### Core Purpose
- Store SBOM files for generations and enhancements with atomic batch semantics
- Provide permanent, stable download URLs for stored manifests
- Integrate with Kafka for error notifications
- Support S3-compatible storage backends (AWS S3, MinIO, etc.)

### Technology Stack

- **Framework**: Quarkus 3.28.2 (Java 17)
- **Build Tool**: Maven
- **Storage**: AWS S3 SDK (S3-compatible backends)
- **Messaging**: Apache Kafka with Avro schemas (Apicurio Registry)
- **Architecture Pattern**: Hexagonal Architecture (Ports and Adapters)
- **Observability**: OpenTelemetry, Micrometer, Prometheus
- **API Documentation**: OpenAPI/Swagger UI
- **Testing**: JUnit 5, Mockito, REST Assured, Testcontainers (MinIO)

### Key Dependencies

- `quarkus-amazon-s3` - S3 client integration
- `quarkus-messaging-kafka` - Kafka integration
- `quarkus-apicurio-registry-avro` - Schema registry for Avro
- `quarkus-micrometer-opentelemetry` - Distributed tracing
- `quarkus-smallrye-health` - Health checks
- `cloudevents-kafka` - CloudEvents support
- `tsid-creator` - Time-sorted unique identifiers
- `lombok` - Boilerplate reduction

## Architecture

The service follows **Hexagonal Architecture** with clear separation of concerns:

### Core Domain (`core/`)
Contains business logic for managing SBOM file storage operations. It is completely agnostic of infrastructure concerns (S3, Kafka, HTTP).

**Key Components:**
- **Domain Models**: `SbomFile` - represents file metadata
- **Ports (Interfaces)**: Define contracts for both driving (API) and driven (SPI) adapters
  - `StorageAdministration` (API) - defines storage operations
  - `ObjectStorage` (SPI) - defines storage backend contract
  - `FailureNotifier` (SPI) - defines error notification contract
- **Services**: `StorageService` - core business logic for atomic batch uploads

### Adapters (`adapter/`)

**Primary Adapters (Driving - `adapter/in/`):**
- `rest/` - REST API endpoints for file uploads and downloads
  - `StorageResource` - main REST controller
  - Exception mappers for consistent error responses

**Secondary Adapters (Driven - `adapter/out/`):**
- `S3StorageAdapter` - S3-compatible storage implementation
- `KafkaFailureNotifier` - Error notification via Kafka
- `exception/` - Domain-specific exceptions

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Primary Adapters                      │
│  (Driving - REST API: StorageResource)                   │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    Core Domain                           │
│  - StorageService (business logic)                       │
│  - SbomFile (domain model)                               │
│  - Ports: StorageAdministration, ObjectStorage           │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                  Secondary Adapters                      │
│  (Driven - S3StorageAdapter, KafkaFailureNotifier)      │
└─────────────────────────────────────────────────────────┘
```

### Event Flow

1. **Inbound**: REST API receives multipart file upload request
2. **Processing**: `StorageService` validates and uploads files atomically to S3
3. **Success**: Returns map of filename → permanent download URL
4. **Failure**: `KafkaFailureNotifier` publishes error to `sbomer.errors` topic

## Building and Running

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker/Podman (for local development)
- Minikube (for full system deployment)

### Local Development Setup

The service is designed to run within the broader SBOMer ecosystem using Helm:

```bash
# 1. Set up the local development environment (Minikube + infrastructure)
bash ./hack/setup-local-dev.sh

# 2. Build and deploy with Helm (includes this service + dependencies)
bash ./hack/run-helm-with-local-build.sh

# 3. Expose the service
kubectl port-forward svc/sbomer-release-manifest-storage-service-chart 8085:8080 -n sbomer-test
```

### Build Commands

```bash
# Compile and run tests
mvn clean verify

# Build with Avro schemas (fetches sbomer-contracts)
bash ./hack/build-with-schemas.sh

# Package for deployment
mvn clean package

# Build native image (GraalVM)
mvn clean package -Pnative

# Run in dev mode (with hot reload)
mvn quarkus:dev

# Sort imports (enforced by build)
mvn impsort:sort
```

### Testing

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify

# Run specific test class
mvn test -Dtest=S3StorageAdapterTest
```

### Configuration

- **Application Config**: `src/main/resources/application.properties`
- **Environment Variables**: 
  - `S3_BUCKET`, `S3_ENDPOINT`, `AWS_REGION` - S3 configuration
  - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` - S3 credentials
  - `S3_PATH_STYLE_ACCESS` - S3 path style setting
  - `KAFKA_BOOTSTRAP_SERVERS` - Kafka brokers
  - `SCHEMA_REGISTRY_URL` - Apicurio Registry URL
  - `SBOMER_STORAGE_PUBLIC_API_URL` - Public API base URL

Key configuration properties:

| Variable | Description | Default |
|----------|-------------|---------|
| `sbomer.storage.public-api-url` | Public API base URL for download links | `http://localhost:8085` |
| `sbomer.storage.s3.bucket` | S3 bucket name | (required) |
| `sbomer.storage.max-file-size-mb` | Maximum file size in MB | `100` |
| `sbomer.storage.max-files-per-batch` | Maximum files per upload | `10` |

## Development Conventions

### Code Organization

- **Package Structure**: Follow hexagonal architecture layers strictly
  - `core/domain/model/` - Domain models
  - `core/port/api/` - Driving port interfaces
  - `core/port/spi/` - Driven port interfaces
  - `core/service/` - Business logic implementations
  - `core/utility/` - Shared utilities
  - `adapter/in/rest/` - REST API endpoints and DTOs
  - `adapter/out/` - External integrations (S3, Kafka)
  - `adapter/out/exception/` - Domain-specific exceptions

### Naming Conventions

- **Domain Models**: Use descriptive names (e.g., `SbomFile`)
- **DTOs**: Suffix with `DTO` (e.g., `MultipartUploadDTO`, `ErrorResponse`)
- **Services**: Suffix with `Service` (e.g., `StorageService`)
- **Resources**: Suffix with `Resource` (e.g., `StorageResource`)
- **Adapters**: Suffix with `Adapter` (e.g., `S3StorageAdapter`)
- **Exception Mappers**: Suffix with `Mapper` (e.g., `StorageExceptionMapper`)

### Coding Standards

1. **Import Organization**: Imports are automatically sorted by `impsort-maven-plugin`
   - Groups: `javax.`, `java.`, `org.`, `com.`
   - Static imports come first
   - Line ending: LF

2. **Lombok Usage**: Use Lombok annotations for boilerplate reduction
   - `@Slf4j` for logging
   - `@Builder` for domain models
   - `@Getter` for immutable fields

3. **Logging Pattern**:
   ```java
   log.info("Action description with context: {}", contextValue);
   log.debug("Detailed information: {}", details);
   log.error("Error occurred: {}", errorContext, exception);
   ```

4. **Exception Handling**:
   - Use domain-specific exceptions in `adapter/out/exception/`
   - Map infrastructure exceptions to domain exceptions
   - Provide clear, actionable error messages
   - Use exception mappers in REST layer for consistent error responses

5. **Resource Management**:
   - Always use try-with-resources for `InputStream` and other closeable resources
   - Document resource lifecycle in method Javadoc
   - Let JAX-RS handle stream closing for response bodies

### Kafka Message Handling

- **Schema Format**: Avro with Apicurio Registry
- **Error Handling**: 
  - **Processing Failures**: Logged and published to `sbomer.errors` topic
  - **CloudEvents Structure**: Use standardized CloudEvents format for error notifications
- **Correlation IDs**: Include correlation IDs for tracing across services

### Logging

- **Format**: Structured with trace context (traceId, spanId, parentId)
- **Levels**: 
  - `INFO` - Default application level
  - `DEBUG` - For `org.jboss.sbomer` package
- **MDC**: Includes OpenTelemetry trace context

### API Design

- **Base Path**: `/api/v1/storage`
- **Documentation**: Available at `/q/swagger-ui` (dev mode)
- **Error Responses**: Use `ErrorResponse` DTO with consistent structure
- **Validation**: Use Hibernate Validator annotations on DTOs
- **Multipart Uploads**: Use `@RestForm` for file uploads

### Testing Practices

- **Unit Tests**: Mock dependencies, test business logic in isolation
  - Example: `S3StorageAdapterTest`, `StorageServiceTest`
- **Integration Tests**: Use Testcontainers for MinIO
  - Example: `S3StorageAdapterIntegrationTest`
- **REST API Tests**: Use REST Assured for endpoint testing
  - Example: `StorageResourceTest`
- **Test Naming**: Use descriptive names that explain the scenario
  - Pattern: `test<Method><Scenario>` (e.g., `testUploadSuccess`, `testUploadNullKey`)
- **Assertions**: Use JUnit 5 assertions
- **Log Capture**: Use LogCaptor for testing log output

### Key Patterns and Practices

1. **Atomic Batch Uploads**:
   - All files in a batch must succeed or the operation fails
   - No automatic rollback (fail-fast approach)
   - Each file's InputStream is opened, used, and closed within the same iteration

2. **Path Security**:
   - Validate all storage keys to prevent path traversal attacks
   - Reject keys containing `..`, starting with `/`, or containing `\`
   - Use regex validation for expected path patterns

3. **Observability**:
   - Use `@WithSpan` for distributed tracing
   - Add `@SpanAttribute` for important context
   - Include traceId, spanId, parentId in log format
   - Expose metrics via Prometheus endpoint

4. **Error Notification**:
   - Publish failures to Kafka topic `sbomer.errors`
   - Use Avro schema with CloudEvents structure
   - Include correlation IDs for tracing
   - Serialize source events as ByteBuffer

5. **API Design**:
   - RESTful endpoints with clear resource hierarchy
   - Multipart form data for file uploads
   - Permanent URLs for downloads via proxy endpoint
   - Comprehensive OpenAPI documentation

### File Upload Flow

```
Client → StorageResource → StorageService → S3StorageAdapter → S3
                ↓ (on error)
         KafkaFailureNotifier → Kafka (sbomer.errors topic)
```

1. Client uploads files via multipart/form-data
2. `StorageResource` validates file size, count, and sanitizes filenames
3. `StorageService` implements atomic batch logic
4. `S3StorageAdapter` handles S3 operations with retry support
5. On success, returns map of filename → permanent URL
6. On failure, publishes error event to Kafka

## API Endpoints

### Upload Generation SBOMs
```
POST /api/v1/storage/generations/{generationId}
Content-Type: multipart/form-data
```

### Upload Enhancement SBOMs
```
POST /api/v1/storage/generations/{generationId}/enhancements/{enhancementId}
Content-Type: multipart/form-data
```

### Download File
```
GET /api/v1/storage/content/{path}
```

## Important Notes

- **S3 Retry Support**: Use `RequestBody.fromBytes()` instead of `RequestBody.fromInputStream()` to enable AWS SDK's built-in retry mechanism
- **Stream Lifecycle**: JAX-RS automatically closes response streams; service layer returns streams directly
- **Path Format**: Storage keys follow pattern `{generationId}/{filename}` or `{generationId}/{enhancementId}/{filename}`
- **Content Type**: Preserve original content type from upload for proper download handling
- **Security**: API key authentication is planned but not yet implemented (WIP)
- **Dev Services**: Quarkus automatically starts Kafka containers in dev mode
- **Native Builds**: Supported but require additional GraalVM configuration for reflection

## Java & Quarkus Best Practices

Follow these guidelines when writing or modifying any Java code in this repository.

### Dependency Injection

- **Prefer constructor injection** over field injection. Constructor injection makes dependencies explicit, simplifies testing, and allows fields to be `final`.
  ```java
  // ✅ Preferred
  @ApplicationScoped
  public class StorageService {
      private final ObjectStorage objectStorage;

      @Inject
      public StorageService(ObjectStorage objectStorage) {
          this.objectStorage = objectStorage;
      }
  }

  // ❌ Avoid
  @ApplicationScoped
  public class StorageService {
      @Inject
      ObjectStorage objectStorage;
  }
  ```
- Use `@ApplicationScoped` as the default CDI scope. Use `@RequestScoped` only when per-request state is genuinely needed. Avoid `@Dependent` unless the lifecycle must follow the injection point.
- Never use `new` to instantiate CDI beans — always let the container manage them.

### Immutability

- Declare fields `final` wherever possible.
- Prefer immutable value objects and records for DTOs (Java `record` types are ideal).
- Use `Collections.unmodifiableList()` / `List.copyOf()` when returning collections from beans.
- Do not expose mutable internal state through getters.

### Exception Handling

- Use **specific, meaningful exceptions** rather than catching `Exception` or `Throwable` broadly.
- Create domain-specific exception classes (e.g., `StorageFileNotFoundException`) in `adapter/out/exception/`.
- Never swallow exceptions silently — at minimum log them at `WARN` or `ERROR` level.
- Use JAX-RS exception mappers to map domain exceptions to HTTP responses consistently.
- Do not use exceptions for normal flow control.

### Lombok Usage

- Use `@Value` for immutable DTOs; use `@Data` only when mutability is genuinely required.
- Prefer `@Builder` over telescoping constructors for classes with many fields.
- Use `@Slf4j` for logger declaration — do not declare `private static final Logger` manually.
- Avoid `@SneakyThrows` — handle or declare checked exceptions explicitly.
- Do not use `@AllArgsConstructor` on CDI beans (it interferes with proxy generation); use `@RequiredArgsConstructor` with `final` fields instead.

### Code Style

- Follow standard Java naming: `camelCase` for methods/variables, `PascalCase` for types, `SCREAMING_SNAKE_CASE` for constants.
- Keep methods short and focused — a method should do one thing. If a method exceeds ~30 lines, consider extracting helper methods.
- Avoid deeply nested code (more than 2–3 levels). Use early returns (guard clauses) to reduce nesting.
- Use `Optional<T>` for return types that may be absent; never return `null` from public methods.
- Annotate overridden methods with `@Override`.
- Use `var` for local variables where the type is obvious from the right-hand side (Java 10+).

### Quarkus-Specific Idioms

- Use `@ConfigProperty` (or `@ConfigMapping` for groups) for injecting configuration values — never use raw `System.getProperty()`.
- Prefer `@QuarkusTest` with `@InjectMock` for unit-level tests; use `@QuarkusIntegrationTest` only for black-box integration scenarios.
- Favour reactive messaging (`@Incoming` / `@Outgoing`) over polling loops for Kafka interactions.
- Do not block the event loop in reactive code paths — offload blocking work with `@Blocking`.

### Null Safety

- Annotate parameters and return types with `@NonNull` / `@Nullable` (from Lombok or Jakarta) to document intent.
- Validate incoming public API parameters with `Objects.requireNonNull()` or Bean Validation annotations (`@NotNull`, `@NotBlank`, etc.).
- Use `Optional` instead of null-returning methods in service and domain layers.

### Logging

- Use `@Slf4j` (Lombok) — the logger field is named `log` by convention in this project.
- Log at `DEBUG` for internal state, `INFO` for significant business events, `WARN` for recoverable issues, `ERROR` for failures that require attention.
- Include structured context in log messages (entity IDs, state transitions) rather than free-form strings.
- Never log sensitive data (credentials, tokens, personal information).

### Testing

- Write tests for every non-trivial code path, including failure/edge cases.
- Use `@ExtendWith(MockitoExtension.class)` for pure unit tests that do not need CDI.
- Prefer `@InjectMocks` + `@Mock` over manual mock construction.
- Name test methods descriptively: `test<Method><Scenario>()`.
- Use standard JUnit 5 assertions or AssertJ's `assertThat(...).isEqualTo(...)` style.
- Do not use `Thread.sleep()` in tests — use Awaitility or reactive testing utilities.

### General Principles

- **SOLID**: Apply Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, and Dependency Inversion principles.
- **DRY**: Extract duplicated logic into shared utilities or base classes, but only when the duplication is identical in intent — do not force unrelated code into a shared abstraction.
- **YAGNI**: Do not add abstractions, parameters, or generalisations "for future use". Implement only what is currently needed.
- **Fail fast**: Validate inputs at system/service boundaries as early as possible.
- **Minimal surface area**: Keep classes, methods, and fields package-private or private unless they must be public. Prefer narrow interfaces.

## Deployment

The service is designed to run in Kubernetes as part of the SBOMer system:
- Deployed via Helm chart in `helm/manifest-storage-service-chart/`
- Integrates with Tekton pipelines for CI/CD
- Exposes health checks via Quarkus SmallRye Health
- Metrics available at `/q/metrics`

## Related Repositories

- **sbomer-contracts**: Avro schema definitions for Kafka events
- **sbomer-local-dev**: Local development setup scripts
- **sbomer-platform**: Platform-level Helm charts and configurations