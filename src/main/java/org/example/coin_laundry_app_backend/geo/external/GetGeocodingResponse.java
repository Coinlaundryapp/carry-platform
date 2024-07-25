package org.example.coin_laundry_app_backend.geo.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record GetGeocodingResponse(
    String status,
    Meta meta,
    List<Address> addresses,
    String errorMessage
) {

    static Logger logger = LoggerFactory.getLogger(GetGeocodingResponse.class);

    public record Meta(
        int totalCount,
        int page,
        int count
    ) {}

    /**
     *
     * @param roadAddress 도로명 주소
     * @param jibunAddress 지번 주소
     * @param englishAddress 영문 주소
     * @param addressElementMap
     * @param x 경도
     * @param y 위도
     * @param distance
     */
    public record Address(
        String roadAddress,
        String jibunAddress,
        String englishAddress,
        Map<AddressType ,AddressElement> addressElementMap,
        String x,
        String y,
        int distance
    ) {
        @JsonCreator
        public Address(
            String roadAddress,
            String jibunAddress,
            String englishAddress,
            List<AddressElement> addressElements,
            String x,
            String y,
            int distance
        ) {
            this(roadAddress, jibunAddress, englishAddress, getAddressElementsMap(addressElements), x, y, distance);
        }

        private static Map<AddressType, AddressElement> getAddressElementsMap(List<AddressElement> addressElements) {
            Map<AddressType, AddressElement> addressElementMap = new EnumMap<>(AddressType.class);
            for (AddressElement addressElement : addressElements) {
                addressElementMap.put(addressElement.type, addressElement);
            }
            return addressElementMap;
        }


        String getAddressElementNameOrNull(AddressType type) {
            AddressElement element = addressElementMap.getOrDefault(type, AddressElement.emptyAddressElementsMap.get(type));
            if (element.isEmpty()) {
                return null;
            }
            return element.longName;
        }
    }

    /**
     * @param type 주소 요소 타입
     * @param longName
     * @param shortName
     * @param code
     */
    public record AddressElement(
        AddressType type,
        String longName,
        String shortName,
        String code
    ) {
        @JsonCreator
        public AddressElement(
            List<AddressType> types,
            String longName,
            String shortName,
            String code
        ) {
            this(getType(types), longName, shortName, code);
        }

        private static AddressType getType(List<AddressType> typeList) {
            if (typeList.size() > 1) {
                logger.warn("AddressElement의 types가 1개 이상입니다. types: {}", typeList);
            }
            if (typeList.isEmpty()) {
                throw new IllegalStateException("AddressElement의 types가 비어있습니다. naver api의 응답이 변경되었을 수 있습니다.");
            }
            return typeList.get(0);
        }

        static final Map<AddressType, AddressElement> emptyAddressElementsMap = new EnumMap<>(AddressType.class) {
            {
                for (AddressType type : AddressType.values()) {
                    put(type, new AddressElement(type, "", "", ""));
                }
            }
        };

        public Boolean isEmpty() {
            return emptyAddressElementsMap.get(type).equals(this);
        }
    }

    public enum AddressType {
        /**
         * 시/도
         */
        SIDO,
        /**
         * 시/군/구
         */
        SIGUGUN,
        /**
         * 읍/면/동
         */
        DONGMYUN,
        /**
         * 리
         */
        RI,
        /**
         * 도로명
         */
        ROAD_NAME,
        /**
         * 건물번호
         */
        BUILDING_NUMBER,
        /**
         * 건물이름
         */
        BUILDING_NAME,
        /**
         * 지번
         */
        LAND_NUMBER,
        /**
         * 우편번호
         */
        POSTAL_CODE,
    }
}

