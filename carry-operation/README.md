# carry-operation

> 운영 대시보드 및 이용약관 관리

## 도메인 개요

운영 현황 요약(오늘의 주문, 배차, 배달 현황)과 운영 이벤트 로그를 제공하는 모듈이다.
이용약관 CRUD 및 약관 버전 관리를 지원하며, 공개 API와 관리자 전용 API를 분리하여 제공한다.

## 아키텍처

```
carry-operation/
├── adapter/
│   ├── in/web/          # TermController, TermAdminController, OperationDashboardController
│   ├── in/event/        # Kafka Event Consumer
│   └── out/persistence/ # JPA Repository
├── application/
│   ├── port/in/         # Use Case 인터페이스
│   └── port/out/        # Outbound Port 인터페이스
└── domain/              # Term, OperationSummary, OperationEvent
```

## API

### 약관 (공개)

| Method | Endpoint                    | 설명        |
|--------|-----------------------------|-----------|
| GET    | `/api/v2/terms`             | 활성 약관 목록  |
| GET    | `/api/v2/terms/required`    | 필수 약관 목록  |

### 약관 (관리자)

| Method | Endpoint                    | 설명       |
|--------|-----------------------------|----------|
| POST   | `/api/v2/admin/terms`       | 약관 생성    |
| PUT    | `/api/v2/admin/terms/{id}`  | 약관 수정    |
| DELETE | `/api/v2/admin/terms/{id}`  | 약관 비활성화  |

### 운영 대시보드 (관리자)

| Method | Endpoint                              | 설명      |
|--------|---------------------------------------|---------|
| GET    | `/api/v2/admin/dashboard/summary`     | 운영 요약   |
| GET    | `/api/v2/admin/dashboard/events`      | 최근 이벤트  |

## 주요 도메인 모델

- **Term**: `title`, `content`, `type`, `required`, `version`, `active`
- **OperationSummary**: `totalOrdersToday`, `pendingDispatches`, `activeDeliveries`, `completedToday`, `cancelledToday`
- **OperationEvent**: `eventType`, `aggregateType`, `aggregateId`, `summary`

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
