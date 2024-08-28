package org.example.coin_laundry_app_backend.user.application.record.address.transform;

import org.example.coin_laundry_app_backend.user.domain.model.enums.AddressType;

public record Content(String addressName, AddressType addressType, RegionAddress regionAddress, RoadAddressSummary roadAddress) {}
