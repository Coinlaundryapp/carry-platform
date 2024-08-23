package org.example.coin_laundry_app_backend.user.application.record.availability;

import lombok.Data;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.enums.City;
import org.example.coin_laundry_app_backend.user.domain.model.enums.District;

public record RegionInfo(
        City city,
        District district
) {}
