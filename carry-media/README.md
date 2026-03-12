# carry-media

> 미디어 파일 업로드 및 다운로드

## 도메인 개요

S3 기반 파일 업로드 및 Presigned URL을 통한 다운로드 기능을 제공하는 모듈이다.
각 파일은 UUID 기반의 `accessKey`로 식별되며, 폴더별로 파일을 정리하여 관리한다.

## 아키텍처

```
carry-media/
├── adapter/
│   ├── in/web/          # REST Controller
│   └── out/persistence/ # JPA Repository
│   └── out/storage/     # S3 Client
├── application/
│   ├── port/in/         # Use Case 인터페이스
│   └── port/out/        # Outbound Port 인터페이스
└── domain/              # MediaResource
```

## API

| Method | Endpoint                              | 설명            |
|--------|---------------------------------------|---------------|
| POST   | `/api/v2/media/upload/{folder}`       | 파일 업로드        |
| GET    | `/api/v2/media/{accessKey}`           | 미디어 정보 조회     |
| GET    | `/api/v2/media/{accessKey}/download`  | 다운로드 URL 발급   |

## 주요 도메인 모델

- **MediaResource**: `folder`, `accessKey`, `originalFilename`, `extension`, `contentType`, `status`, `fileSize`, `uploadedBy`

## 의존 관계

- `carry-common`
- `carry-infra-persistence`
- `carry-infra-s3`
