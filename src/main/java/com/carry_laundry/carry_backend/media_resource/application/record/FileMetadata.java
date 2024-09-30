package com.carry_laundry.carry_backend.media_resource.application.record;

public record FileMetadata(
    String filename,
    String contentType,
    long contentLength
) {

}
