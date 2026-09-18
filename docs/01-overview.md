# 01. 프로젝트 개요

> 최종 수정일: 2026-03-11 (초안) · 상태 갱신: 2026-09-19 (주문 흐름 서술 정정 2026-09-08)
> 상태: **초기 설계 문서 — 일부 방향 정정됨(아래 배너)**

> ⚠️ **이 문서는 2026-03 초기 설계 드래프트다.** 이후 실제 결정으로 갈라진 부분:
> - **마이크로서비스 전환·서비스 메시(Linkerd/Flagger)는 채택하지 않았다** — ADR-0007에서 모듈러 모놀리스
>   유지로 확정([0007](adr/0007-modular-monolith-over-microservices.md), 과분해 재평가는 [0006](adr/0006-module-decomposition-reassessment.md)).
>   K8s/MSA seam은 트리거(트래픽 격차·팀 분리·클라우드 준비) 도달 시에만 분리하도록 ADR에 문서화만 해 둠.
> - 현재 진행 상태·완료 범위(Phase 1~7)는 루트 [`ROADMAP.md`](../ROADMAP.md)가 사실원.
> - 클라이언트(고객·캐리어·코디네이터 3종) 완성형 계획은 별도 프론트 로드맵 문서.
> 아래 V1 회고·전환 배경은 사료로서 그대로 둔다.

---

## Carry란

**Carry**는 코인 세탁 배달 서비스다. 고객이 세탁물을 맡기면 세탁소에서 세탁 후 배송해주는 O2O 서비스로,
물리 흐름(주문 → 배차 → 수거 → 세탁 → 배송)과 결제 흐름(수거 완료 시 인보이스 발행 → 자동과금 → 재시도/연체)이
병렬로 진행되는 두 개의 사가로 구성된다. 결제는 물리 흐름의 게이트가 아니며, 주문 생성 시점에는 빌링키 보유와
연체 없음만 확인한다. 상세는 [06-saga.md](06-saga.md) 참조 (2026-09-08 정정: 이전 판은 "주문 → 결제 → 배차 → …" 순서로 적었다).

## 프로젝트의 목적

본 프로젝트는 사업 투자 유치에 실패한 이후, **서비스로서의 완성도를 갖추는 데에 집중**하기로 결정했다.
실서비스 운영이 아닌, 다음 역량을 증명할 수 있는 포트폴리오 프로젝트를 목표로 한다:

- 복합 도메인(주문, 결제, 배송, 지도)을 다루는 **모듈러 모놀리스** 설계
- **CDC(Change Data Capture)** 기반 이벤트 아키텍처
- **Choreography Saga**를 통한 분산 트랜잭션 처리
- **OpenTelemetry** 기반 관측 가능성
- ~~**Linkerd + Flagger** 기반 서비스 메시와 카나리 배포~~ — **미채택**(ADR-0007, 클라우드 미준비)
- ~~모놀리스에서 **마이크로서비스로의 점진적 전환** 실증~~ — **보류**: 분리 seam만 ADR-0007에 문서화, 실제 분리는 안 함

## 서비스가 다루는 도메인

| 도메인 | 설명 |
|--------|------|
| **주문 (Order)** | 세탁 주문 생성, 상태 관리, 가격 산정 |
| **결제 (Payment)** | PG 연동, 결제 승인/취소/환불 |
| **배차 (Dispatch)** | 라이더 매칭, 수거/배송 배차 |
| **운영 (Operation)** | 운영자 대시보드, 주문/배차 모니터링 |
| **회원 (User)** | 인증(OAuth), 회원 정보, 배송지 관리 |
| **세탁소 (Laundromat)** | 세탁소 정보, 위치 기반 검색 |
| **리뷰 (Review)** | 세탁소 리뷰 작성/조회 |
| **알림 (Notification)** | 카카오톡/SMS 알림 발송 |
| **위치 (Geo)** | 지오코딩, 역지오코딩 |
| **미디어 (Media)** | S3 파일 업로드/다운로드 |
| **가격 (Price)** | 세탁 옵션별 가격 정책 |
| **서비스 가용성 (Service Availability)** | 지역별 서비스 가능 여부 |

## V1 회고

### V1 현황 (중단 시점)

V1은 두 가지 형태로 존재한다:

**모놀리스 (carry-backend)**
- Spring Boot 3.3.0, Java 17, WebFlux, R2DBC, PostgreSQL
- ~229개 Java 파일, 14개 모듈
- 주요 기능 구현되었으나 프로덕션 준비 부족 (테스트 6개, CI/CD 없음, 시크릿 하드코딩)

**마이크로서비스 시도 (carry-order-service, carry-dispatch-service)**
- Spring Boot 3.4.1, WebFlux, R2DBC, Kafka, OpenTelemetry
- CDC 패턴(Outbox + Debezium) 골격 구현
- Order: Outbox 저장까지, Dispatch: Debezium envelope 파싱까지 구현 후 중단

### 중단 원인 분석

1. **WebFlux의 디버깅 난이도**: 리액티브 스트림에서 발생하는 에러의 스택 트레이스가 불명확하고, 비즈니스 로직 디버깅이 매우 어려움
2. **리액티브 디버깅 모드의 딜레마**: `Hooks.onOperatorDebug()` 활성화 시 성능 저하가 발생하며, 이를 감안할 바에는 동기식 코드가 더 합리적
3. **도메인 복잡도와 기술 복잡도의 충돌**: 주문-결제-배송 Saga 같은 복잡한 비즈니스 흐름을 리액티브 체인으로 구현하면 코드 가독성이 심각하게 저하됨
4. **마이크로서비스 인프라 부담**: 소규모 팀(4~5명)에서 서비스 분리를 동시에 진행하니 인프라 구성이 도메인 개발을 압도

### 전환 결정

**비동기 요건을 코드 레벨(WebFlux)이 아닌 인프라 레벨(Kafka + Debezium)로 밀어낸다.**

서비스 내부는 동기식으로 단순하게, 서비스 간 통신은 이벤트로 비동기 처리한다.
물리적 서비스 분리 대신 모듈러 모놀리스로 논리적 경계만 확보하고, 필요 시 분리한다.

## 문서 구조

| 문서 | 내용 |
|------|------|
| [01-overview.md](01-overview.md) | 프로젝트 개요, V1 회고, 전환 배경 (본 문서) |
| [02-tech-decisions.md](02-tech-decisions.md) | 기술 스택 결정 및 근거 |
| [03-architecture.md](03-architecture.md) | 아키텍처 원칙, 모듈 구조, 내부 구조 |
| [04-module-communication.md](04-module-communication.md) | 모듈 간 통신, DB 전략 |
| [05-cdc-outbox.md](05-cdc-outbox.md) | Transactional Outbox + Debezium CDC |
| [06-saga.md](06-saga.md) | Saga 설계 (주문 프로세스) |
| [07-observability.md](07-observability.md) | Observability(구현됨) + 서비스 메시(미채택) |
| [08-testing.md](08-testing.md) | 테스트 전략 (단위 ~ E2E, 마이크로서비스 테스트) |
| [09-roadmap.md](09-roadmap.md) | (SUPERSEDED) 초기 MSA 분리·배포 구상 — 실제는 루트 ROADMAP.md |
| [10-production-readiness.md](10-production-readiness.md) | 프로덕션 준비도 점검 |
| [11-business-metrics.md](11-business-metrics.md) | 비즈니스 메트릭 카탈로그 (사실원) |
| [12-observability-stack.md](12-observability-stack.md) | 관측 스택 구성 (Prometheus·Grafana·알럿) |
| [13-logging-policy.md](13-logging-policy.md) | 로깅 정책 — 상관관계·레벨 분류 |
| [14-client-retry-guide.md](14-client-retry-guide.md) | 클라이언트 재시도 가이드 (에러 코드·멱등성·백오프) |
| [15-invariant-catalog.md](15-invariant-catalog.md) | 불변식 카탈로그 (애그리거트·사가·스위퍼 규칙, 코드에서 역추출) |
| [16-context-map.md](16-context-map.md) | 컨텍스트 맵 (바운디드 컨텍스트 관계·DDD 라벨) |
| [17-ubiquitous-language.md](17-ubiquitous-language.md) | 유비쿼터스 언어 사전 (용어·불일치 목록) |
| [adr/](adr/) | 아키텍처 의사결정 기록 (ADR-0001~0009) |
