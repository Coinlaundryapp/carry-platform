package org.example.coin_laundry_app_backend.user.application.record.address.fetch;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Meta(
        @JsonProperty("is_end") boolean isEnd,
        @JsonProperty("pageable_count") int pageableCount,
        @JsonProperty("total_count") int totalCount
) {}