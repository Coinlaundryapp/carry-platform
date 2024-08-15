package org.example.coin_laundry_app_backend.user.presentation.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoUserTermsResponse {

    private Long id;
    private ServiceTerm[] serviceTerms;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceTerm {

        private String tag;
        private Boolean required;
        private Boolean agreed;
        private Boolean revocable;
    }

}
