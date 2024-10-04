package com.carry_laundry.carry_backend.user.application.service;

import com.carry_laundry.carry_backend.user.application.record.address.fetch.Document;
import com.carry_laundry.carry_backend.user.application.record.address.fetch.FetchApiResponse;
import com.carry_laundry.carry_backend.user.application.record.address.fetch.Meta;
import com.carry_laundry.carry_backend.user.application.record.address.transform.Content;
import com.carry_laundry.carry_backend.user.application.record.address.transform.Pagination;
import com.carry_laundry.carry_backend.user.application.record.address.transform.RegionAddress;
import com.carry_laundry.carry_backend.user.application.record.address.transform.RoadAddressSummary;
import com.carry_laundry.carry_backend.user.presentation.payload.response.address.SearchAddressResponse;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Setter
@Service
@ConfigurationProperties(prefix = "settings.external.address-search-api.kakao")
public class AddressSearchService {

    private String baseUrl;
    private String apiKey;
    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders((headers) -> {
                    headers.add("Authorization", "KakaoAK " + apiKey);
                }
            )
            .build();
    }

    public Mono<SearchAddressResponse> fetchAndTransformData(String query, int pageNumber,
        int pageSize) {
        return webClient.get()
            .uri(uriBuilder ->
                uriBuilder
                    .queryParam("query", query)
                    .queryParam("page", pageNumber)
                    .queryParam("size", pageSize)
                    .build()
            ).retrieve()
            .bodyToMono(FetchApiResponse.class)
            .map(fetchApiResponse -> transformApiResponse(fetchApiResponse, pageNumber, pageSize));
    }

    private SearchAddressResponse transformApiResponse(FetchApiResponse apiResponse, int pageNumber,
        int pageSize) {
        List<Content> contentList = apiResponse.documents().stream()
            .map(this::transformDocumentToContent)
            .collect(Collectors.toList());

        Meta meta = apiResponse.meta();
        Pagination pagination = new Pagination(
            pageNumber,
            pageSize,
            (int) Math.ceil((double) meta.totalCount() / pageSize),
            meta.totalCount(),
            !meta.isEnd()
        );

        return new SearchAddressResponse(contentList, pagination);
    }

    private Content transformDocumentToContent(Document document) {
        return new Content(
            document.addressName(),
            document.addressType(),
            getRegionAddress(document),
            getRoadAddressSummary(document)
        );
    }

    private RegionAddress getRegionAddress(Document document) {
        if (document.address() != null) {
            return new RegionAddress(document.address().addressName());
        } else {
            return new RegionAddress(document.roadAddress().region1depthName() +
                " " + document.roadAddress().region2depthName() +
                " " + document.roadAddress().region3depthName());
        }
    }

    private RoadAddressSummary getRoadAddressSummary(Document document) {
        if (document.roadAddress() != null) {
            return new RoadAddressSummary(
                document.roadAddress().addressName(),
                document.roadAddress().buildingName() != null ? document.roadAddress()
                    .buildingName() : ""
            );
        } else {
            return null;
        }
    }
}
