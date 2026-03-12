# carry-service-availability

> 서비스 권역 및 운영시간 관리

## 도메인 개요

서비스 가용 권역을 관리하고, 요일별 운영시간 및 휴무일(공휴일) 오버라이드를 설정하는 모듈이다.
주문 시 해당 권역의 서비스 가용성을 검증하는 역할을 담당한다.
외부 API 없이 내부 포트를 통해 다른 모듈에서 사용된다.

## 아키텍처

```
carry-service-availability/
├── adapter/
│   └── out/persistence/ # JPA Repository
├── application/
│   ├── port/in/         # Use Case 인터페이스
│   └── port/out/        # Outbound Port 인터페이스
└── domain/              # ServiceArea, OperatingSchedule, HolidayOverride
```

## API

외부 API 없음 (내부 모듈 전용, 포트를 통해 소비됨)

## 주요 도메인 모델

- **ServiceArea**: `areaCode`, `areaName`, `status`, `operatingSchedules`, `holidayOverrides`
- **OperatingSchedule**: `dayOfWeek`, `openTime`, `closeTime`
- **HolidayOverride**: `date`, `reason`, `closed`

## 의존 관계

- `carry-common`
- `carry-infra-persistence`
