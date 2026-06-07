package com.carry.audit.port

import com.carry.audit.domain.AuditAction

/**
 * 감사 로그 기록 아웃바운드 포트. 도메인 애플리케이션 서비스가 감사 저장/컨텍스트 수집 인프라를
 * 직접 알지 않도록 분리한다.
 *
 * actor·role·ip·traceId는 구현(어댑터)이 호출 시점의 ambient 컨텍스트(SecurityContext/MDC/
 * RequestContext)에서 수집하므로 호출자는 **무엇을 바꿨는지**만 전달한다.
 *
 * 호출은 호출 서비스의 트랜잭션 안에서 동기 실행된다(액션과 원자).
 */
interface AuditPort {

    /**
     * 민감 작업 한 건을 감사한다.
     *
     * @param before 변이 전 상태(임의 객체, 어댑터가 JSON 직렬화). 없으면 null.
     * @param after 변이 후 상태(임의 객체). 없으면 null.
     */
    fun record(action: AuditAction, targetType: String, targetId: String, before: Any?, after: Any?)
}
