# carry-laundromat

> 세탁소 등록 및 위치 기반 검색

## 도메인 개요

세탁소 등록 및 수정, 주변 세탁소 검색(위치 기반, 반경 설정) 기능을 담당하는 모듈이다. 세탁소의 서비스 옵션 관리와 이미지 관리 기능을 제공한다.

## 아키텍처

```
adapter/inbound/rest/     # REST API
adapter/outbound/         # 외부 의존 구현
application/port/         # 유스케이스 인터페이스
application/service/      # 비즈니스 로직
domain/model/             # 엔티티
domain/vo/                # 값 객체
domain/exception/         # 도메인 예외
```

## API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/laundromats` | 주변 세탁소 검색 |
| GET | `/api/v1/laundromats/{id}` | 상세 조회 |
| POST | `/api/v1/laundromats` | 등록 |
| PUT | `/api/v1/laundromats/{id}` | 정보 수정 |
| PUT | `/api/v1/laundromats/{id}/options` | 옵션 수정 |
| POST | `/api/v1/laundromats/{id}/media` | 이미지 추가 |
| DELETE | `/api/v1/laundromats/{id}/media/{mediaId}` | 이미지 삭제 |

## 주요 도메인 모델

- **Laundromat** — `name`, `address`, `location`, `options`, `mediaResources`
- **NearbyLaundromat** — `laundromat`, `distanceMeters`

## 의존 관계

- `carry-common`
- `carry-infra-persistence`
