# carry-geo

> 지오코딩 및 역지오코딩 서비스

## 도메인 개요

주소-좌표 간 변환을 담당하는 모듈이다. 통합/지번/도로명 주소를 좌표로 변환하고, 좌표를 주소로 역변환하는 기능을 제공한다. 외부 Geocoding API와 연동한다.

## 아키텍처

```
adapter/inbound/rest/     # REST API
adapter/outbound/         # 외부 의존 구현 (Geocoding API 클라이언트)
application/port/         # 유스케이스 인터페이스
application/service/      # 비즈니스 로직
domain/model/             # 엔티티
domain/vo/                # 값 객체
domain/exception/         # 도메인 예외
```

## API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v2/geo/geocode` | 주소 → 좌표 |
| GET | `/api/v2/geo/geocode/jibun` | 지번 주소 → 좌표 |
| GET | `/api/v2/geo/geocode/road` | 도로명 주소 → 좌표 |
| GET | `/api/v2/geo/reverse-geocode` | 좌표 → 주소 |

## 주요 도메인 모델

- **GeocodingResult** — `jibunAddress`, `roadAddress`, `coordinate`, `addressComponent`
- **ReverseGeocodingResult** — `country`, `si`, `gu`, `dong`
- **Coordinate** — `latitude`, `longitude`

## 의존 관계

- `carry-common`
