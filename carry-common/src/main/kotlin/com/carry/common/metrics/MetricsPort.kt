package com.carry.common.metrics

import java.time.Duration

/**
 * 도메인/애플리케이션 서비스가 Micrometer 같은 관측성 라이브러리를 직접 알지 않도록 분리하는 아웃바운드 포트.
 *
 * 메트릭 이름은 호출 측이 결정한다(`carry.order.created.total` 같은 도메인 카운터).
 * 구현체는 인프라 어셈블리(carry-infra-observability)에 위치한다.
 */
interface MetricsPort {
    fun incrementCounter(name: String, vararg tags: Pair<String, String>)

    fun recordTimer(name: String, duration: Duration, vararg tags: Pair<String, String>)
}
