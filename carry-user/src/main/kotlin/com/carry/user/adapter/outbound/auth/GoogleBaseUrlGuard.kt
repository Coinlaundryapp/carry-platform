package com.carry.user.adapter.outbound.auth

import jakarta.annotation.PostConstruct
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * prod 프로파일에서 `google.api.base-url`이 실 Google이 아니면 부팅을 막는다.
 * 잘못된 prod base-url = 위조 신원 수용이므로 코드로 봉인한다(e2e 스텁의 prod 누출 방지).
 */
@Component
class GoogleBaseUrlGuard(
    private val properties: GoogleProperties,
    private val environment: Environment,
) {
    @PostConstruct
    fun verify() {
        val isProd = environment.activeProfiles.contains(PROD_PROFILE)
        check(!(isProd && properties.baseUrl != GOOGLE_PROD_URL)) {
            "prod 프로파일에서 google.api.base-url은 반드시 $GOOGLE_PROD_URL 이어야 합니다 (현재: ${properties.baseUrl})"
        }
    }

    companion object {
        const val PROD_PROFILE = "prod"
        const val GOOGLE_PROD_URL = "https://openidconnect.googleapis.com"
    }
}
