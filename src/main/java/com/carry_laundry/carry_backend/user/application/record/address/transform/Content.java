package com.carry_laundry.carry_backend.user.application.record.address.transform;

import com.carry_laundry.carry_backend.user.domain.model.enums.AddressType;

public record Content(String addressName, AddressType addressType, RegionAddress regionAddress,
                      RoadAddressSummary roadAddress) {

}
