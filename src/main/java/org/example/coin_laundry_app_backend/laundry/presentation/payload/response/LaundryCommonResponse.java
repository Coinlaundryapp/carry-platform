package org.example.coin_laundry_app_backend.laundry.presentation.payload.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.example.coin_laundry_app_backend.laundry.domain.model.enums.LaundryOption;

@Getter
public class LaundryCommonResponse {

    // From Laundry Entity
    private final Long id;
    private final String name;
    private final String address;
    private final double latitude;
    private final double longitude;
    private final double distance;
    // TODO: How Could I get laundryPrice from db?
    private int laundryPrice;

    // From LaundryOptionMapping Entity
    private final List<LaundryOption> options;
    // From LaundryImage Entity
    private final List<String> imageUrls;

    // TODO: Review
    private double reviewAverageRating;
    private int reviewCount;

    @Builder
    protected LaundryCommonResponse(Long id, String name, String address, double latitude,
        double longitude, double distance, List<LaundryOption> options, List<String> imageUrls) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
        this.options = options;
        this.imageUrls = imageUrls;
    }

}
