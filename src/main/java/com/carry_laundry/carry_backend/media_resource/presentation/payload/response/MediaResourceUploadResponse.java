package com.carry_laundry.carry_backend.media_resource.presentation.payload.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MediaResourceUploadResponse {

    private List<String> urls;

}
