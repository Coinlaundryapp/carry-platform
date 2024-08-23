package org.example.coin_laundry_app_backend.user.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.geo.application.service.ReverseGeocodingService;
import org.example.coin_laundry_app_backend.geo.application.service.model.ReverseGeoModel;
import org.example.coin_laundry_app_backend.geo.domain.model.EPSG4326Coordinate;
import org.example.coin_laundry_app_backend.user.application.record.availability.InspectionResult;
import org.example.coin_laundry_app_backend.user.application.record.availability.RegionInfo;
import org.example.coin_laundry_app_backend.user.domain.converter.AvailabilityNotificationConverter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.AvailabilityNotification;
import org.example.coin_laundry_app_backend.user.domain.model.enums.City;
import org.example.coin_laundry_app_backend.user.domain.model.enums.District;
import org.example.coin_laundry_app_backend.user.domain.model.enums.ServiceAvailabilityLevel;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.availability.AvailabilityQueryRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.availability.CreateNotificationRequest;
import org.example.coin_laundry_app_backend.user.repository.ServiceAvailabilityNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServiceAvailabilityService {

    private final ServiceAvailabilityNotificationRepository notificationRepository;
    private final ReverseGeocodingService reverseGeocodingService;

    @Transactional
    public Mono<InspectionResult> query(AvailabilityQueryRequest request) {
        return reverseGeocodingService.getReverseGeocoding(
                new EPSG4326Coordinate(request.latitude(), request.longitude()))
                .map(this::inspect);
    }

    private InspectionResult inspect(ReverseGeoModel geoModel) {
        Set<String> validCityNameSet = Set.of("서울특별시", "인천광역시", "안양시", "김포시", "부천시", "광명시", "성남시", "구리시");
        Set<String> districtNameSet = Set.of("은평구", "계양구");

        String cityDesc = geoModel.si();
        String districtDesc = geoModel.gu();
        if(cityDesc.contains("도")) {
            if(geoModel.gu().split(" ").length > 1) {
                cityDesc = geoModel.gu().split(" ")[0];
                districtDesc = geoModel.gu().split(" ")[1];
            } else {
                cityDesc = geoModel.gu();
                districtDesc = null;
            }
        }

        if (validCityNameSet.contains(cityDesc)) {
            City city = City.from(cityDesc);
            if(districtNameSet.contains(districtDesc)) {
                District district = District.from(districtDesc);
                RegionInfo regionInfo = new RegionInfo(city, district);
                return new InspectionResult(ServiceAvailabilityLevel.AVAILABLE, regionInfo);
            }else {
                RegionInfo regionInfo = new RegionInfo(city, null);
                return new InspectionResult(ServiceAvailabilityLevel.POTENTIALLY_AVAILABLE, regionInfo);
            }
        }else {
            RegionInfo regionInfo = new RegionInfo(null, null);
            return new InspectionResult(ServiceAvailabilityLevel.UNAVAILABLE, regionInfo);
        }
    }

    @Transactional
    public Mono<AvailabilityNotification> register(CreateNotificationRequest request) {
        AvailabilityNotification availabilityNotification = AvailabilityNotification.create(request.region().city().toString(), request.region().district().toString(), request.notificationType(), request.contact());
        return notificationRepository.save(AvailabilityNotificationConverter.toData(availabilityNotification)).map(AvailabilityNotificationConverter::toDomain);
    }
}
