package org.jboss.sbomer.manifest.storage.service.adapter.in.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/storage")
@Tag(name = "Storage", description = "Operations for uploading SBOMs and retrieving permanent download links.")
public class StorageResource {

    @Inject
    StorageAdministration storageService;

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
            @Parameter(description = "The Generation ID", required = true) @PathParam("generationId") String genId,
            @RestForm("files") List<FileUpload> uploads) {
        return handleUpload(uploads, (files) -> storageService.storeGenerationSboms(genId, files));
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
                    // POINT TO THE DTO HERE:
                    schema = @Schema(implementation = MultipartUploadDTO.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "Files uploaded successfully. Returns a map of Filename -> Permanent URL.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON)
    )
    public Response uploadEnhancement(
            @Parameter(description = "The Generation ID", required = true) @PathParam("generationId") String genId,
            @Parameter(description = "The Enhancement ID", required = true) @PathParam("enhancementId") String enhId,
            @RestForm("files") List<FileUpload> uploads) {
        return handleUpload(uploads, (files) -> storageService.storeEnhancementSboms(genId, enhId, files));
    }

    @GET
    @Path("/content/{path: .*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download File", description = "Streams the content of a stored file based on its storage key path.")
    public Response download(@PathParam("path") String path) {
        try {
            java.io.InputStream stream = storageService.getFileContent(path);
            String filename = path.substring(path.lastIndexOf('/') + 1);
            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @FunctionalInterface
    interface UploadAction {
        Map<String, String> execute(List<SbomFile> files);
    }

    private Response handleUpload(List<FileUpload> uploads, UploadAction action) {
        if (uploads == null || uploads.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No files provided").build();
        }
        try {
            List<SbomFile> domainFiles = new ArrayList<>();
            for (FileUpload upload : uploads) {
                domainFiles.add(SbomFile.builder()
                        .filename(upload.fileName())
                        .contentType(upload.contentType())
                        .size(upload.size())
                        .content(java.nio.file.Files.newInputStream(upload.uploadedFile()))
                        .build());
            }
            return Response.ok(action.execute(domainFiles)).build();
        } catch (IOException e) {
            return Response.serverError().entity("File processing error").build();
        }
    }
}



