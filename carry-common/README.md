# carry-common

> 공통 응답 래퍼, 예외 체계, 유틸리티

## 개요

모든 모듈이 공통으로 사용하는 응답 래퍼, 에러 코드, 예외 처리 체계를 제공하는 기반 모듈이다.
비즈니스 예외 클래스와 전역 예외 핸들러를 포함하며, API 표준 응답 형식을 정의한다.

## 아키텍처

```
carry-common/
├── exception/
│   ├── ErrorCode.kt              # 비즈니스 에러 코드 enum (~50개)
│   └── BusinessException.kt      # 비즈니스 예외 기반 클래스
├── response/
│   └── ApiResponse.kt            # 표준 API 응답 래퍼
└── config/
    └── GlobalExceptionHandler.kt # 전역 예외 처리 (@RestControllerAdvice)
```

## 주요 컴포넌트

| 컴포넌트                    | 설명                                              |
|--------------------------|---------------------------------------------------|
| `ApiResponse<T>`         | 표준 API 응답 래퍼 (`status`, `code`, `message`, `data`) |
| `ErrorCode`              | 비즈니스 에러 코드 enum (~50개)                          |
| `BusinessException`      | 비즈니스 예외 기반 클래스                                   |
| `GlobalExceptionHandler` | 전역 예외 처리 (`@RestControllerAdvice`)                |

## 의존 관계

외부 모듈 의존 없음. 아래 의존성을 `api` 스코프로 제공:

- `spring-web`
- `spring-boot-starter-validation`
- `springdoc-openapi`
