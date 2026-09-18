package com.carry.infra.persistence

/**
 * 자식 컬렉션을 도메인 소스로 "전체 교체"한다(clear 후 transform 결과 추가).
 * orphanRemoval=true 자식의 기존 관용구(clear()+add/addAll)와 동작이 동일하다.
 * MutableCollection 수신자라 List·Set 모두 적용 가능.
 *
 * 식별자 보존이 필요한 경우(예: Delivery.steps의 stepType 기준 in-place 갱신)에는
 * 사용하지 말 것 — 이 함수는 자식 행을 삭제 후 재삽입한다.
 */
fun <E, S> MutableCollection<E>.replaceAllFrom(source: Iterable<S>, transform: (S) -> E) {
    clear()
    source.forEach { add(transform(it)) }
}
