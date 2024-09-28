package org.example.coin_laundry_app_backend.user.application.record.address.fetch;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.coin_laundry_app_backend.user.domain.enums.AddressType;

public record Document(
        @JsonProperty("address_name") String addressName,
        @JsonProperty("address_type") AddressType addressType,
        @JsonProperty("address") Address address,
        @JsonProperty("road_address") RoadAddress roadAddress
) {}
