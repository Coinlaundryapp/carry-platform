package org.example.coin_laundry_app_backend.user.application.record.availability;

import org.example.coin_laundry_app_backend.user.domain.model.enums.City;
import org.example.coin_laundry_app_backend.user.domain.model.enums.District;

public record AvailableRegion(
        City city,
        District district,
        double latitude,
        double longitude
) {
}
