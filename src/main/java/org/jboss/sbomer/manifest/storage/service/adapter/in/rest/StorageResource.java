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
import org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto.MultipartUploadDTO;
import org.jboss.sbomer.manifest.storage.service.core.domain.model.SbomFile;
import org.jboss.sbomer.manifest.storage.service.core.port.api.StorageAdministration;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;

/**
 * REST resource for SBOM storage operations.
 * Handles file uploads with validation and sanitization.
 */
@Path("/api/v1/storage")
@Tag(name = "Storage", description = "Operations for uploading SBOMs and retrieving permanent download links.")
@Slf4j
public class StorageResource {

    @Inject
    StorageAdministration storageService;

    @ConfigProperty(name = "sbomer.storage.max-file-size-mb", defaultValue = "100")
    int maxFileSizeMb;

    @ConfigProperty(name = "sbomer.storage.max-files-per-batch", defaultValue = "10")
    int maxFilesPerBatch;

    @POST
    @Path("/generations/{generationId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload Generation SBOMs", description = "Uploads one or more files associated with a specific Generation ID.")
    @RequestBody(
            description = "The files to upload",
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA,
                    schema = @Schema(implementation = MultipartUploadDTO.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Files uploaded successfully. Returns a map of Filename -> Permanent URL.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    example = "{\"bom.json\": \"https://host/api/v1/storage/content/gen-123/bom.json\"}"
            )
    )
    public Response uploadGeneration(
            @Parameter(description = "The Generation ID", required = true)
            @PathParam("generationId")
            @NotBlank
            String genId,
            @RestForm("files") List<FileUpload> uploads) {
        return handleUpload(uploads, files -> storageService.storeGenerationSboms(genId, files));
    }

    @POST
    @Path("/generations/{generationId}/enhancements/{enhancementId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload Enhancement SBOMs", description = "Uploads one or more files associated with a specific Enhancement step.")
    @RequestBody(
            description = "The files to upload",
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA,
                    schema = @Schema(implementation = MultipartUploadDTO.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Files uploaded successfully. Returns a map of Filename -> Permanent URL.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
    )
    public Response uploadEnhancement(
            @Parameter(description = "The Generation ID", required = true)
            @PathParam("generationId")
            @NotBlank
            String genId,
            @Parameter(description = "The Enhancement ID", required = true)
            @PathParam("enhancementId")
            @NotBlank
            String enhId,
            @RestForm("files") List<FileUpload> uploads) {
        return handleUpload(uploads, files -> storageService.storeEnhancementSboms(genId, enhId, files));
    }

    /**
     * Downloads a file from storage.
     * 
     * Uses StreamingOutput to ensure proper InputStream lifecycle management.
     * The InputStream is opened when streaming starts and automatically closed
     * when streaming completes or fails.
     */
    @GET
    @Path("/content/{path: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download File", description = "Streams the content of a stored file based on its storage key path.")
    public Response download(
            @Parameter(description = "The storage path", required = true)
            @PathParam("path")
            @NotBlank
            String path) {
        
        String filename = path.substring(path.lastIndexOf('/') + 1);
        
        // Use StreamingOutput to properly manage InputStream lifecycle
        // The stream is opened lazily when JAX-RS starts writing the response
        // and automatically closed when done
        StreamingOutput streamingOutput = output -> {
            try (InputStream stream = storageService.getFileContent(path)) {
                stream.transferTo(output);
            }
        };
        
        return Response.ok(streamingOutput)
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
}


