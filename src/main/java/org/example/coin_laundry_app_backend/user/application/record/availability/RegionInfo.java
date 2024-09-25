package org.example.coin_laundry_app_backend.user.application.record.availability;

import org.example.coin_laundry_app_backend.user.domain.enums.City;
import org.example.coin_laundry_app_backend.user.domain.enums.District;

public record RegionInfo(
        City city,
        District district
) {}
