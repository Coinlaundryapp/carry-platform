package org.example.coin_laundry_app_backend.user.application.record.availability;

import org.example.coin_laundry_app_backend.user.domain.model.enums.ServiceAvailabilityLevel;

public record InspectionResult(
        ServiceAvailabilityLevel serviceAvailabilityLevel,
        RegionInfo region
) {}