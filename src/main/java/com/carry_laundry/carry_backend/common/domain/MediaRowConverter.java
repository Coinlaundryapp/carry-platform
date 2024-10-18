package com.carry_laundry.carry_backend.common.domain;

import com.carry_laundry.carry_backend.common.presentation.payload.MediaCommonResponse;
import com.carry_laundry.carry_backend.review.domain.entity.ReviewMediaResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaRowConverter {

    private final ObjectMapper objectMapper;

    public List<MediaCommonResponse> convertToCommonResponse(String[] source) {
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

    public List<ReviewMediaResource> convertToReviewMediaResource(String json) {
        if (json == null || json.equals("[null]")) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReviewMediaResource>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
