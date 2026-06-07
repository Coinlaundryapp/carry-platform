package com.carry.app.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 시간 처리 단일 소스(ROADMAP 5.1).
 *
 * 애플리케이션 서비스는 이 [Clock] 빈을 주입받아 `clock.instant()`로 현재 시각을 계산하고,
 * 순수 도메인 모델에는 그 결과를 `now: Instant`로 전달한다. 도메인은 `java.time.Clock`에
 * 의존하지 않으며 시간을 외부 입력으로 받는 순수 함수가 된다.
 *
 * UTC 고정: 도메인 시각은 `Instant`(타임존 무관)이며, 기존 refresh 토큰 저장소의
 * `Clock.systemUTC()` 선례와 일치한다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
