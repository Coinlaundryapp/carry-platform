# carry-user

> 사용자 프로필 및 배송지 관리

## 도메인 개요

사용자 회원가입 및 프로필 관리를 담당하는 모듈이다. 배송지 CRUD(최대 5개)와 기본 배송지 설정 기능을 제공한다.

## 아키텍처

```
adapter/inbound/rest/     # REST API (UserController, ShippingAddressController)
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
| GET | `/api/v1/users/me` | 내 프로필 조회 |
| PUT | `/api/v1/users/me` | 프로필 수정 |
| DELETE | `/api/v1/users/me` | 회원 탈퇴 |
| GET | `/api/v1/shipping-addresses` | 배송지 목록 |
| POST | `/api/v1/shipping-addresses` | 배송지 등록 |
| PUT | `/api/v1/shipping-addresses/{id}` | 배송지 수정 |
| DELETE | `/api/v1/shipping-addresses/{id}` | 배송지 삭제 |
| PUT | `/api/v1/shipping-addresses/{id}/default` | 기본 배송지 설정 |

## 주요 도메인 모델

- **User** — `email`, `name`, `phone`, `role`, `isActive`
- **ShippingAddress** — `alias`, `address`, `coordinates`, `recipientName`, `recipientPhone`, `areaCode`

## 의존 관계

- `carry-common`
- `carry-infra-persistence`
