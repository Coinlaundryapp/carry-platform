package com.carry_laundry.carry_backend.user.application.record.availability;

import com.carry_laundry.carry_backend.user.domain.enums.City;
import com.carry_laundry.carry_backend.user.domain.enums.District;

public record AvailableRegion(
    City city,
    District district,
    double latitude,
    double longitude
) {

}
