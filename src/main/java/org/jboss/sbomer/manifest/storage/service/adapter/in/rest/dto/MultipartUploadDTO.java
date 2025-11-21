package org.jboss.sbomer.manifest.storage.service.adapter.in.rest.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "MultipartUpload", description = "Schema for uploading multiple files via Multipart Form Data")
public class MultipartUploadDTO {
    
    @Schema(
        name = "files", 
        description = "The list of files to upload", 
        type = SchemaType.ARRAY,
        format = "binary" // This triggers the file picker in Swagger UI
    )
    public List<String> files;
}
