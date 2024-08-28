package org.example.coin_laundry_app_backend.user.application.record.address.fetch;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Address(
        @JsonProperty("address_name") String addressName,
        @JsonProperty("main_address_no") String mainAddressNo,
        @JsonProperty("sub_address_no") String subAddressNo,
        @JsonProperty("region_1depth_name") String region1depthName,
        @JsonProperty("region_2depth_name") String region2depthName,
        @JsonProperty("region_3depth_name") String region3depthName
) {}
