package com.carry_laundry.carry_backend.user.application.record.availability;

import com.carry_laundry.carry_backend.user.domain.enums.ServiceAvailabilityLevel;

public record InspectionResult(
    ServiceAvailabilityLevel serviceAvailabilityLevel,
    RegionInfo region
) {

}