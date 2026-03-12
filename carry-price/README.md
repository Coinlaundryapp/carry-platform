# carry-price

> 세탁 가격 정책 관리 및 금액 계산

## 도메인 개요

주문 유형(단위/요청/품목)별 가격 정책 CRUD를 담당하는 모듈이다. 옵션별 가격 설정과 총 금액 계산 기능을 제공한다.

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
| GET | `/api/v1/prices` | 가격 정책 조회 |
| POST | `/api/v1/prices/calculate` | 총 금액 계산 |
| POST | `/api/v1/prices` | 가격 정책 생성 |
| PUT | `/api/v1/prices/{id}/options` | 옵션 가격 수정 |
| DELETE | `/api/v1/prices/{id}` | 가격 정책 삭제 |

## 주요 도메인 모델

- **PricePolicy** — `condition`, `optionPrices`
- **PriceCondition** — `orderUnitType`, `orderRequestType`, `laundryItemType`
- **OptionPrice** — `optionType`, `subOptionType`, `price`, `selectable`

## 의존 관계

- `carry-common`
- `carry-infra-persistence`
