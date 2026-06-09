# Architecture Decision Records (ADR)

이 디렉토리는 carry-platform의 핵심 아키텍처 의사결정 기록(ADR)을 담는다.

## ADR이란?

ADR(Architecture Decision Record)은 **중요한 아키텍처 결정**을 그 *맥락*(왜 이 문제가 생겼는가),
*결정*(무엇을 택했는가), *결과*(어떤 트레이드오프를 받아들였는가), *고려한 대안*(왜 다른 길을
택하지 않았는가)과 함께 기록하는 문서다. "코드가 무엇을 하는가"는 코드가 말해주지만,
"왜 그렇게 했는가"는 시간이 지나면 사라진다 — ADR은 그 *왜*를 보존한다.

## 인덱스

| ID | 제목 | 상태 | 날짜 |
|----|------|------|------|
| [0001](0001-domain-jpa-separation.md) | 도메인 모델과 JPA 엔티티 분리 | Accepted | 2026-06-09 |
| [0002](0002-outbox-cdc-over-dual-write.md) | Outbox + CDC(Debezium) 채택 — dual-write 회피 | Accepted | 2026-06-09 |
| [0003](0003-module-decomposition-criteria.md) | 모듈 분리 기준 | Accepted | 2026-06-09 |
| [0004](0004-choreography-saga.md) | Choreography Saga 채택 | Accepted | 2026-06-09 |

## 상태 정의

- **Proposed**: 논의 중
- **Accepted**: 결정·구현 완료
- **Deprecated**: 더 이상 유효하지 않음
- **Superseded**: 다른 ADR로 대체됨 (대체 ADR 번호 명기)

## 결정 간 관계

```
ADR-0003 (모듈 분리 기준)
  ├─ 도메인 모듈 간 직접 의존 금지
  │     └─ ADR-0004 (Choreography Saga) — 이벤트로만 통신 → 중앙 오케스트레이터 기각 근거
  ├─ 모듈 내부 헥사고날 일관
  │     └─ ADR-0001 (도메인/JPA 분리) — 어댑터-포트로 영속성 경계
  └─ 이벤트 전파 인프라
        └─ ADR-0002 (Outbox + CDC) — 신뢰성·순서 보장하는 이벤트 발행
```

네 결정은 "모듈러 모놀리스 + 신뢰성 있는 이벤트 기반 분산 워크플로우"라는 하나의 일관된
아키텍처를 이룬다.

## 새 ADR 작성 시

`docs/adr/NNNN-<kebab-title>.md` (4자리 일련번호) 형식으로 추가하고 위 인덱스 표에 한 줄 등록한다.
기존 결정을 뒤집는 경우 새 ADR을 만들고 옛 ADR 상태를 `Superseded by ADR-NNNN`으로 갱신한다.
