# carry-review

> 세탁소 리뷰 및 평점 관리

## 도메인 개요

세탁소에 대한 리뷰 작성, 수정, 삭제 기능을 제공하는 모듈이다.
세탁소별 리뷰 조회 시 커서 기반 페이지네이션을 지원하며, 평균 평점 및 총 개수 등 리뷰 통계를 관리한다.
본인이 작성한 리뷰만 수정 및 삭제할 수 있다.

## 아키텍처

```
carry-review/
├── adapter/
│   ├── in/web/          # REST Controller
│   └── out/persistence/ # JPA Repository
├── application/
│   ├── port/in/         # Use Case 인터페이스
│   └── port/out/        # Outbound Port 인터페이스
└── domain/              # Review, ReviewStatistics
```

## API

| Method | Endpoint                                         | 설명        |
|--------|--------------------------------------------------|-----------|
| POST   | `/api/v2/reviews`                                | 리뷰 작성     |
| PUT    | `/api/v2/reviews/{id}`                           | 리뷰 수정     |
| DELETE | `/api/v2/reviews/{id}`                           | 리뷰 삭제     |
| GET    | `/api/v2/reviews/{id}`                           | 리뷰 상세     |
| GET    | `/api/v2/reviews/laundromat/{id}`                | 세탁소 리뷰 목록 |
| GET    | `/api/v2/reviews/my`                             | 내 리뷰 목록   |
| GET    | `/api/v2/reviews/laundromat/{id}/statistics`     | 리뷰 통계     |

## 주요 도메인 모델

- **Review**: `laundromatId`, `customerId`, `comment`, `rating`, `mediaUrls`
- **ReviewStatistics**: `laundromatId`, `totalCount`, `averageRating`

## 의존 관계

- `carry-common`
- `carry-infra-persistence`
