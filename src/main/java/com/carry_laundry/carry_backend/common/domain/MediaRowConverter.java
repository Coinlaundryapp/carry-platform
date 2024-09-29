package com.carry_laundry.carry_backend.common.domain;

import com.carry_laundry.carry_backend.common.presentation.payload.MediaCommonResponse;
import java.util.Arrays;
import java.util.List;
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
