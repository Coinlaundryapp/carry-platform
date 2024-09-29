package com.carry_laundry.carry_backend.user.presentation.payload.request.availability;

import org.springframework.lang.NonNull;

public record QueryAvailabilityRequest(
        @NonNull double latitude,
        @NonNull double longitude
) {}
