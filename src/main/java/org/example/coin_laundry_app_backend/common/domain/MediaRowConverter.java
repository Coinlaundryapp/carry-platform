package org.example.coin_laundry_app_backend.common.domain;

import java.util.Arrays;
import java.util.List;
import org.example.coin_laundry_app_backend.common.presentation.payload.MediaCommonResponse;
import org.springframework.stereotype.Component;

@Component
public class MediaRowConverter {

    public List<MediaCommonResponse> readConvert(String[] source) {
        if (source == null) {
            return List.of();
        }
        return Arrays.stream(source)
            .map(mediaResource -> {
                String[] mediaResourceSplit = mediaResource.replaceAll("[()]", "").split(",");
                return new MediaCommonResponse(mediaResourceSplit[0], mediaResourceSplit[1]);
            })
            .toList();
    }

}
