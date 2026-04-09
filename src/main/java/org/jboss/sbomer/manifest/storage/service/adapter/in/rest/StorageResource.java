package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.ErrorResponse;
import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.MultipartUploadDTO;
import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for SBOM storage operations.
 * Handles file uploads with validation and sanitization.
 */
@Path("/api/v1/storage")
@Tag(name = "Storage", description = "Operations for uploading SBOMs and retrieving permanent download links.")
@Slf4j
public class StorageResource {

    private final StorageAdministration storageService;
    private final int maxFileSizeMb;
    private final int maxFilesPerBatch;

    @Inject
    public StorageResource(
            StorageAdministration storageService,
            @ConfigProperty(name = "sbomer.storage.max-file-size-mb", defaultValue = "100") int maxFileSizeMb,
            @ConfigProperty(name = "sbomer.storage.max-files-per-batch", defaultValue = "10") int maxFilesPerBatch) {
        this.storageService = storageService;
        this.maxFileSizeMb = maxFileSizeMb;
        this.maxFilesPerBatch = maxFilesPerBatch;
    }

    @POST
    @Path("/generations/{generationId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload Generation SBOMs", description = "Uploads one or more files associated with a specific Generation ID.")
    @RequestBody(description = "The files to upload", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = @Schema(implementation = MultipartUploadDTO.class)))
    @APIResponse(responseCode = "200", description = "Files uploaded successfully. Returns a map of Filename -> Permanent URL.", content = @Content(mediaType = MediaType.APPLICATION_JSON, example = "{\"bom.json\": \"https://host/api/v1/storage/content/gen-123/bom.json\"}"))
    @APIResponse(responseCode = "400", description = "Bad Request - Invalid input (empty files, file too large, too many files, invalid generation ID)", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - Access denied to storage", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal Server Error - Unexpected error occurred", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "Service Unavailable - Storage service temporarily unavailable", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response uploadGeneration(
            @Parameter(description = "The Generation ID", required = true) @PathParam("generationId") @NotBlank String genId,
            @RestForm("files") List<FileUpload> uploads) {
        return handleUpload(uploads, files -> storageService.storeGenerationSboms(genId, files));
    }

    @POST
    @Path("/generations/{generationId}/enhancements/{enhancementId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload Enhancement SBOMs", description = "Uploads one or more files associated with a specific Enhancement step.")
    @RequestBody(description = "The files to upload", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = @Schema(implementation = MultipartUploadDTO.class)))
    @APIResponse(responseCode = "200", description = "Files uploaded successfully. Returns a map of Filename -> Permanent URL.", content = @Content(mediaType = MediaType.APPLICATION_JSON))
    @APIResponse(responseCode = "400", description = "Bad Request - Invalid input (empty files, file too large, too many files, invalid IDs)", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - Access denied to storage", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal Server Error - Unexpected error occurred", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "Service Unavailable - Storage service temporarily unavailable", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response uploadEnhancement(
            @Parameter(description = "The Generation ID", required = true) @PathParam("generationId") @NotBlank String genId,
            @Parameter(description = "The Enhancement ID", required = true) @PathParam("enhancementId") @NotBlank String enhId,
            @RestForm("files") List<FileUpload> uploads) {
        return handleUpload(uploads, files -> storageService.storeEnhancementSboms(genId, enhId, files));
    }

    /**
     * Downloads a file from storage.
     *
     * Opens the InputStream before creating the Response to enable fail-fast error handling.
     * Exceptions propagate to exception mappers for unified error handling.
     * JAX-RS automatically manages the InputStream lifecycle, closing it after the response
     * is fully streamed to the client.
     */
    @GET
    @Path("/content/{path: .*}")
    @Operation(summary = "Download File", description = "Streams the content of a stored file based on its storage key path.")
    @APIResponse(responseCode = "200", description = "File downloaded successfully", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM))
    @APIResponse(responseCode = "400", description = "Bad Request - Invalid path format or path traversal attempt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - Access denied to storage", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Not Found - File does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal Server Error - Unexpected error occurred", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "503", description = "Service Unavailable - Storage service temporarily unavailable", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response download(
            @Parameter(description = "The storage path", required = true) @PathParam("path") @NotBlank String path) {

        validateStoragePath(path);

        InputStream stream = storageService.getFileContent(path);

        String filename = path.substring(path.lastIndexOf('/') + 1);

        return Response.ok(stream)
                .type(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    @FunctionalInterface
    interface UploadAction {
        Map<String, String> execute(List<SbomFile> files);
    }

    private Response handleUpload(List<FileUpload> uploads, UploadAction action) {
        validateUploads(uploads);

        List<SbomFile> domainFiles = uploads.stream()
                .peek(this::validateFileUpload)
                .map(upload -> SbomFile.builder()
                        .filename(upload.fileName())
                        .contentType(upload.contentType())
                        .size(upload.size())
                        .filePath(upload.uploadedFile())
                        .build())
                .collect(Collectors.toList());

        Map<String, String> result = action.execute(domainFiles);
        return Response.ok(result).build();
    }

    private void validateUploads(List<FileUpload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new WebApplicationException("No files provided", Response.Status.BAD_REQUEST);
        }
        if (uploads.size() > maxFilesPerBatch) {
            throw new WebApplicationException(
                    String.format("Too many files. Maximum %d files per batch", maxFilesPerBatch),
                    Response.Status.BAD_REQUEST);
        }
    }

    private void validateFileUpload(FileUpload upload) {
        long maxFileSizeBytes = maxFileSizeMb * 1024L * 1024L;
        if (upload.size() > maxFileSizeBytes) {
            throw new WebApplicationException(
                    String.format("File '%s' exceeds maximum size of %d MB",
                            upload.fileName(), maxFileSizeMb),
                    Response.Status.BAD_REQUEST);
        }
        if (upload.fileName() == null || upload.fileName().trim().isEmpty()) {
            throw new WebApplicationException("Filename cannot be empty", Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Validates storage path to prevent path traversal attacks.
     *
     * @param path the storage path to validate
     * @throws WebApplicationException if path is invalid or contains malicious
     *                                 patterns
     */
    private void validateStoragePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new WebApplicationException("Path cannot be empty", Response.Status.BAD_REQUEST);
        }

        // Prevent path traversal attacks
        if (path.contains("..") || path.startsWith("/") || path.contains("\\")) {
            log.warn("Path traversal attempt detected: {}", path);
            throw new WebApplicationException(
                    "Invalid path: path traversal not allowed",
                    Response.Status.BAD_REQUEST);
        }

        // Validate expected pattern: gen-xxx/file.json or gen-xxx/enh-yyy/file.json
        // Allows alphanumeric, hyphens, underscores in directory names
        // Allows alphanumeric, dots, hyphens, underscores in filenames
        if (!path.matches("^[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)?/[a-zA-Z0-9_.-]+$")) {
            log.warn("Invalid path format: {}", path);
            throw new WebApplicationException(
                    "Invalid path format",
                    Response.Status.BAD_REQUEST);
        }
    }

}
