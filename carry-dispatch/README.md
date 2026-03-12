# carry-dispatch

> 배차 배정, 선점 및 배달원 권역 관리

## 도메인 개요

배차 자동/수동 배정과 배달원 선점/수락/거절을 담당하는 모듈이다. 배달원 권역 등록 및 해제, 코디네이터 배차 관리 기능을 제공한다. `DispatchCreatedEvent`, `DispatchAssignedEvent`를 발행하여 사가에 참여한다.

## 아키텍처

```
adapter/inbound/rest/     # REST API (DispatchCarrierController, DispatchCoordinatorController, CarrierAreaController)
adapter/outbound/         # 외부 의존 구현
application/port/         # 유스케이스 인터페이스
application/service/      # 비즈니스 로직
domain/model/             # 엔티티
domain/vo/                # 값 객체
domain/exception/         # 도메인 예외
```

## API

### 배달원 배차

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v2/dispatches/available` | 수락 가능 배차 목록 |
| POST | `/api/v2/dispatches/{id}/claim` | 배차 선점 |
| POST | `/api/v2/dispatches/{id}/accept` | 배차 수락 |
| POST | `/api/v2/dispatches/{id}/reject` | 배차 거절 |
| GET | `/api/v2/dispatches/my` | 내 배차 목록 |
| GET | `/api/v2/dispatches/{id}` | 배차 상세 |

### 코디네이터 배차

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v2/coordinator/dispatches/{id}/assign` | 코디네이터 배차 배정 |
| POST | `/api/v2/coordinator/dispatches/{id}/cancel` | 코디네이터 배차 취소 |
| GET | `/api/v2/coordinator/dispatches/carriers` | 권역별 배달원 조회 |

### 배달원 권역

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v2/carrier-areas` | 권역 등록 |
| DELETE | `/api/v2/carrier-areas` | 권역 해제 |
| GET | `/api/v2/carrier-areas` | 배달원 권역 목록 |

## 주요 도메인 모델

- **Dispatch** — `orderId`, `laundromatId`, `status`, `carrierId`, `areaCode`, `desiredPickupAt`, `assignedBy`
- **CarrierArea** — `carrierId`, `areaCode`, `areaName`, `active`

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
