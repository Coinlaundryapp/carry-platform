# carry-delivery

> 수거-세탁-건조-배달 프로세스 관리

## 도메인 개요

배달 단계별 프로세스(수거 -> 세탁 -> 건조 -> 배달)를 관리하는 모듈이다.
각 단계마다 사진 기록을 남기고, 수거 시 무게 측정 및 가격 계산을 트리거한다.
배달 완료 시 `DeliveryCompletedEvent`를 발행하여 후속 처리를 진행한다.

## 아키텍처

```
carry-delivery/
├── adapter/
│   ├── in/web/          # REST Controller
│   └── out/persistence/ # JPA Repository
├── application/
│   ├── port/in/         # Use Case 인터페이스
│   └── port/out/        # Outbound Port 인터페이스
└── domain/              # Delivery, DeliveryStep
```

## API

| Method | Endpoint                              | 설명        |
|--------|---------------------------------------|-----------|
| GET    | `/api/v2/deliveries/my`               | 배달원 배달 목록 |
| GET    | `/api/v2/deliveries/{id}`             | 배달 상세     |
| POST   | `/api/v2/deliveries/{id}/pickup`      | 수거 완료     |
| POST   | `/api/v2/deliveries/{id}/washing`     | 세탁 시작     |
| POST   | `/api/v2/deliveries/{id}/drying`      | 건조 완료     |
| POST   | `/api/v2/deliveries/{id}/delivery`    | 배달 완료     |

## 주요 도메인 모델

- **Delivery**: `orderId`, `dispatchId`, `carrierId`, `laundromatId`, `status`, `actualWeight`, `steps`
- **DeliveryStep**: `stepType`, `status`, `mediaIds`, `note`, `completedAt`

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
