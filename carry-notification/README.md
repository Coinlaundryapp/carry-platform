# carry-notification

> 알림 발송 및 조회

## 도메인 개요

주문 및 배달 관련 알림을 발송하고 조회하는 모듈이다.
PUSH, SMS, EMAIL 등 다양한 알림 채널을 지원하며, 각 알림의 상태(PENDING, SENT, FAILED)를 관리한다.

## 아키텍처

```
carry-notification/
├── adapter/
│   ├── in/web/          # REST Controller
│   ├── in/event/        # Kafka Event Consumer
│   └── out/persistence/ # JPA Repository
├── application/
│   ├── port/in/         # Use Case 인터페이스
│   └── port/out/        # Outbound Port 인터페이스
└── domain/              # Notification
```

## API

| Method | Endpoint                        | 설명      |
|--------|---------------------------------|---------|
| GET    | `/api/v2/notifications/my`      | 내 알림 목록 |
| GET    | `/api/v2/notifications/{id}`    | 알림 상세   |

## 주요 도메인 모델

- **Notification**: `recipientId`, `recipientContact`, `type`, `channel`, `title`, `content`, `status`, `referenceType`, `referenceId`, `sentAt`, `failReason`

## 의존 관계

- `carry-common`
- `carry-event`
- `carry-infra-persistence`
- `carry-infra-kafka`
