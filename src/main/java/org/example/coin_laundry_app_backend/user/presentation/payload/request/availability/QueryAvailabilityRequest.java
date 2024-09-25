package org.example.coin_laundry_app_backend.user.presentation.payload.request.availability;

import org.springframework.lang.NonNull;

public record QueryAvailabilityRequest(
        @NonNull double latitude,
        @NonNull double longitude
) {}
