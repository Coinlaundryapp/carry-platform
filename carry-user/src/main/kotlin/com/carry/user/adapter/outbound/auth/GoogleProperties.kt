package com.carry.user.adapter.outbound.auth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Google API 설정. base-url은 기본 운영값이며, e2e/stg에서만 env로 스텁 서버를 가리킬 수 있다.
 * (prod에서 스텁을 가리키면 위조 신원 수용 → GoogleBaseUrlGuard가 부팅을 막는다.)
 */
@ConfigurationProperties(prefix = "google.api")
data class GoogleProperties(
    val baseUrl: String = "https://openidconnect.googleapis.com",
)
