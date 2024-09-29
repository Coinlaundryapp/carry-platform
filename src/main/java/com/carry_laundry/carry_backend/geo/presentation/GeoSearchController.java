package com.carry_laundry.carry_backend.geo.presentation;

import com.carry_laundry.carry_backend.geo.application.service.GeocodingService;
import com.carry_laundry.carry_backend.geo.application.service.ReverseGeocodingService;
import com.carry_laundry.carry_backend.geo.application.service.model.GeoModel;
import com.carry_laundry.carry_backend.geo.application.service.model.JibunGeoModel;
import com.carry_laundry.carry_backend.geo.application.service.model.ReverseGeoModel;
import com.carry_laundry.carry_backend.geo.application.service.model.RoadGeoCoding;
import com.carry_laundry.carry_backend.geo.domain.model.EPSG4326Coordinate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * TODO geo coding POC를 위한 controller 해당 컨트롤러는 POC를 위한 것이므로 삭제 예정
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class GeoSearchController {

    private final ReverseGeocodingService reverseGeocodingService;
    private final GeocodingService geocodingService;

    @GetMapping("/gc")
    Mono<List<GeoModel>> getGeoCoding(String address) {
        return geocodingService.getGeocoding(address);
    }

    @GetMapping("/gc/jibun")
    Mono<List<JibunGeoModel>> getJibunGeoCoding(String address) {
        return geocodingService.getJibunGeocoding(address);
    }

    @GetMapping("/gc/road/{address}")
    Mono<List<RoadGeoCoding>> getRoadGeoCoding(@PathVariable String address) {
        return geocodingService.getRoadGeocoding(address);
    }

    @GetMapping("/rgc")
    Mono<ReverseGeoModel> getReverseGeocoding(EPSG4326Coordinate coordinate) {
        return reverseGeocodingService.getReverseGeocoding(coordinate);
    }
}
