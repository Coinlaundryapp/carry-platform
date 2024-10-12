package com.carry_laundry.carry_backend.media_resource.application.record;

import java.util.UUID;

public record FileUploadStatus(
    String originalFilename,
    UUID accessKey,
    Boolean uploadSuccess
) {

    public static FileUploadStatus success(String originalFilename, UUID accessKey) {
        return new FileUploadStatus(originalFilename, accessKey, Boolean.TRUE);
    }

    public static FileUploadStatus error(String originalFilename, UUID accessKey) {
        return new FileUploadStatus(originalFilename, accessKey, Boolean.FALSE);
    }
}
