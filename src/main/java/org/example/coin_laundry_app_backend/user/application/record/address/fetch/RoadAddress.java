package org.example.coin_laundry_app_backend.user.application.record.address.fetch;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoadAddress(
        @JsonProperty("address_name") String addressName,
        @JsonProperty("building_name") String buildingName,
        @JsonProperty("main_building_no") String mainBuildingNo,
        @JsonProperty("sub_building_no") String subBuildingNo,
        @JsonProperty("region_1depth_name") String region1depthName,
        @JsonProperty("region_2depth_name") String region2depthName,
        @JsonProperty("region_3depth_name") String region3depthName,
        @JsonProperty("road_name") String road_name
) {}
