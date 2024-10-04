package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.geo.application.service.ReverseGeocodingService;
import com.carry_laundry.carry_backend.geo.application.service.model.ReverseGeoModel;
import com.carry_laundry.carry_backend.geo.domain.model.EPSG4326Coordinate;
import com.carry_laundry.carry_backend.user.application.record.availability.AvailableRegion;
import com.carry_laundry.carry_backend.user.application.record.availability.InspectionResult;
import com.carry_laundry.carry_backend.user.application.record.availability.RegionInfo;
import com.carry_laundry.carry_backend.user.domain.entity.AvailabilityNotification;
import com.carry_laundry.carry_backend.user.domain.enums.City;
import com.carry_laundry.carry_backend.user.domain.enums.District;
import com.carry_laundry.carry_backend.user.domain.enums.ServiceAvailabilityLevel;
import com.carry_laundry.carry_backend.user.presentation.payload.request.availability.CreateNotificationRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.request.availability.QueryAvailabilityRequest;
import com.carry_laundry.carry_backend.user.repository.ServiceAvailabilityNotificationRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ServiceAvailabilityService {

    private final ServiceAvailabilityNotificationRepository notificationRepository;
    private final ReverseGeocodingService reverseGeocodingService;

    public Mono<List<AvailableRegion>> getAvailableRegions() {
        // TODO Query from information table
        return Mono.just(List.of(
            new AvailableRegion(City.SEOUL_SI, District.EUNPYEONG_GU_SEOUL, 37.6027, 126.9291),
            new AvailableRegion(City.INCHEON_SI, District.GYEYANG_GU_INCHEON, 37.5374, 126.7377)
        ));
    }

    @Transactional
    public Mono<InspectionResult> query(QueryAvailabilityRequest request) {
        return reverseGeocodingService.getReverseGeocoding(
                new EPSG4326Coordinate(request.latitude(), request.longitude()))
            .map(this::inspect);
    }

    // FIXME Move to Domain Services
    private InspectionResult inspect(ReverseGeoModel geoModel) {
        Set<String> validCityNameSet = Set.of("서울특별시", "인천광역시", "안양시", "김포시", "부천시", "광명시", "성남시",
            "구리시");
        Set<String> districtNameSet = Set.of("은평구", "계양구");

        String cityDesc = geoModel.si();
        String districtDesc = geoModel.gu();
        if (cityDesc.contains("도")) {
            if (geoModel.gu().split(" ").length > 1) {
                cityDesc = geoModel.gu().split(" ")[0];
                districtDesc = geoModel.gu().split(" ")[1];
            } else {
                cityDesc = geoModel.gu();
                districtDesc = null;
            }
        }

        if (validCityNameSet.contains(cityDesc)) {
            City city = City.from(cityDesc);
            if (districtNameSet.contains(districtDesc)) {
                District district = District.from(districtDesc);
                RegionInfo regionInfo = new RegionInfo(city, district);
                return new InspectionResult(ServiceAvailabilityLevel.AVAILABLE, regionInfo);
            } else {
                RegionInfo regionInfo = new RegionInfo(city, null);
                return new InspectionResult(ServiceAvailabilityLevel.POTENTIALLY_AVAILABLE,
                    regionInfo);
            }
        } else {
            RegionInfo regionInfo = new RegionInfo(null, null);
            return new InspectionResult(ServiceAvailabilityLevel.UNAVAILABLE, regionInfo);
        }
    }

    @Transactional
    public Mono<AvailabilityNotification> register(CreateNotificationRequest request) {
        return notificationRepository.save(AvailabilityNotification.create(request));
    }
}
