package org.example.coin_laundry_app_backend.common.presentation.payload;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaCommonResponse {

    private String mediaUrl;
    private String extension;

    public MediaCommonResponse(String mediaUrl, String extension) {
        this.mediaUrl = mediaUrl;
        this.extension = extension;
    }
}
