package com.carry_laundry.carry_backend.user.application.record.address.fetch;

import com.carry_laundry.carry_backend.user.domain.model.enums.AddressType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Document(
    @JsonProperty("address_name") String addressName,
    @JsonProperty("address_type") AddressType addressType,
    @JsonProperty("address") Address address,
    @JsonProperty("road_address") RoadAddress roadAddress
) {

}
