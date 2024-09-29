package com.carry_laundry.carry_backend.user.application.record.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoUserTermsResponse {

    @Getter
    private Long id;
    private List<ServiceTerm> serviceTerms;


    public List<ServiceTerm> getServiceTerms() {
        return serviceTerms == null ? new ArrayList<>() : serviceTerms;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceTerm {

        private String tag;
        private Boolean required;
        private Boolean agreed;
        private Boolean revocable;
    }

}
