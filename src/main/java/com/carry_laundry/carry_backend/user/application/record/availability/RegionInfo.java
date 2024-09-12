package com.carry_laundry.carry_backend.user.application.record.availability;

import com.carry_laundry.carry_backend.user.domain.model.enums.City;
import com.carry_laundry.carry_backend.user.domain.model.enums.District;

public record RegionInfo(
    City city,
    District district
) {

}
