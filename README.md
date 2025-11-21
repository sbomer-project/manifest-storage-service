# Manifest Storage Service

The **Manifest Storage Service** is a dedicated microservice within the SBOMer NextGen architecture responsible for the persistent storage and retrieval of generated and enhanced manifests.

It acts as an abstraction layer over object storage (S3), providing a clean REST API for atomic batch uploads and serving permanent download links for downstream consumers.

## Architecture

This service follows **Hexagonal Architecture (Ports and Adapters)**:

* **Core Domain:** Handles file pathing strategies (`{generationId}/{filename}`) and atomicity logic (all files in a batch must succeed or none are indexed).
* **Primary Port (Driving):** A REST API (`StorageResource`) used by Generators or Enhancers to upload files and to download them by other system components or end-users.
* **Secondary Port (Driven) (WIP):** An interface (`ObjectStorage`) to talk to S3 compatible storage. Currently implemented with an In-Memory adapter for local development, S3 to be implemented.

## Features

* **Atomic Batch Uploads:** Supports uploading multiple files for a generation/enhancement (e.g., `bom.json`, `bom2.json`, ...) in a single HTTP request. If one upload fails, the operation returns an error to prevent partial state.
* **Permanent URLs:** Generates stable, permanent URLs for accessing stored content via a proxy endpoint.
* **Security (WIP):** Write operations are secured via an API Key (Configurable via `sbomer.api.secret`). (To be implemented)
* **S3 Compatibility (WIP):** Seamless integration with S3 or local MinIO instances. (Currently has a mock in-memory implementation)

## API Documentation

When running locally, full OpenAPI documentation and Swagger UI are available:

* **Swagger UI:** [http://localhost:8085/q/swagger-ui](http://localhost:8085/q/swagger-ui)
* **OpenAPI JSON:** `/q/openapi`

### Key Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/storage/generations/{genId}` | Uploads a batch of files for a base generation. |
| `POST` | `/api/v1/storage/generations/{genId}/enhancements/{enhId}` | Uploads a batch of files for a specific enhancement step. |
| `GET` | `/api/v1/storage/content/{path}` | Proxies the file content from storage to the client. |

## Configuration

The application is configured via `application.properties` or environment variables.

| Property                  | Env Variable | Description                                                           | Default |
|:--------------------------| :--- |:----------------------------------------------------------------------| :--- |
| `sbomer.api.secret` (WIP) | `SBOMER_API_SECRET` | The shared secret required for upload operations. (To be implemented) | `sbomer-secret-key` |
| `sbomer.storage.api-url`  | `SBOMER_STORAGE_API_URL` | The public base URL used to construct download links.                 | `http://localhost:8085` |

## Getting Started (Local Development)

This component is designed to run alongside the wider SBOMer system using Podman Compose.

### 1. Start the Infrastructure
Run the compose script from the root of the project repository:

```shell script
bash ./hack/run-compose.sh
```

This will spin up the manifest-storage-service on port 8085 along with the latest Quay images of the other components of the system.

### 2. Manual Testing (Curl)

You can test it out by uploading files using curl.

#### Step 1: Create dummy files

```shell script
echo '{"bom": "data"}' > sbom.json
echo '{"bom": "data2"}' > sbom2.json
```

Step 2: Upload Batch (Generation)

```shell script
# Note: secret key to be implemented later, can drop it for now
curl -v -X POST \
  -H "X-API-Key: sbomer-secret-key" \
  -H "Content-Type: multipart/form-data" \
  -F "files=@sbom.json" \
  -F "files=@sbom2.json" \
  http://localhost:8085/api/v1/storage/generations/gen-test-123  
```
Response:

JSON
```json
{
  "sbom.json": "http://localhost:8085/api/v1/storage/content/gen-test-123/sbom.json",
  "sbom.spdx": "http://localhost:8085/api/v1/storage/content/gen-test-123/sbom.spdx"
}
```

#### Step 3: Download File Copy one of the URLs from the response and open it in your browser or curl it:

```shell script
curl http://localhost:8085/api/v1/storage/content/gen-test-123/sbom.json
```

### Kubernetes / Tekton Integration

When running inside a Kubernetes TaskRun for example, the upload step can utilize the service like this:

```yaml
- name: upload-to-storage
  image: curlimages/curl:latest
  env:
    - name: API_KEY
      valueFrom:
        secretKeyRef:
          name: sbomer-secrets
          key: api-key
  script: |
    RESPONSE=$(curl -s -f -X POST \
      -H "X-API-Key: $API_KEY" \
      -H "Content-Type: multipart/form-data" \
      -F "files=@/workspace/sbom.json" \
      http://manifest-storage-service:8080/api/v1/storage/generations/$(params.generation-id))
    
    # Extract URL and save to Tekton results
    echo $RESPONSE | grep -o '"sbom.json":"[^"]*"' | cut -d'"' -f4 > $(results.sbom-url.path)
```