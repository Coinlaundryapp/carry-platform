package com.carry_laundry.carry_backend.common.presentation.payload;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaCommonResponse {

    private String mediaUri;
    private String extension;

    public MediaCommonResponse(String mediaUri, String extension) {
        this.mediaUri = mediaUri;
        this.extension = extension;
    }
}
