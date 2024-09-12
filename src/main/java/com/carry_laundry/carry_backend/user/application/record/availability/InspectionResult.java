package com.carry_laundry.carry_backend.user.application.record.availability;

import com.carry_laundry.carry_backend.user.domain.model.enums.ServiceAvailabilityLevel;

public record InspectionResult(
    ServiceAvailabilityLevel serviceAvailabilityLevel,
    RegionInfo region
) {

}