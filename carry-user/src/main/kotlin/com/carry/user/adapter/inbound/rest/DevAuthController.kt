package com.carry.user.adapter.inbound.rest

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.common.response.ApiResponse
import com.carry.user.adapter.inbound.rest.dto.DevLoginRequest
import com.carry.user.adapter.inbound.rest.dto.TokenResponse
import com.carry.user.application.port.inbound.AuthUseCase
import com.carry.user.domain.vo.UserRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 비프로덕션 dev-login — Kakao 콘솔 의존 없이 역할별 토큰을 발급해 로컬/개발에서 인증을 도달 가능하게 한다.
 *
 * **노출 봉인 2중화**:
 *  1. `@Profile("local | dev")` — prod 프로파일에선 빈 자체가 등록되지 않는다(엔드포인트 부재).
 *  2. [DevLoginGuard] `@PostConstruct` — 혹시 `dev,prod`처럼 모순된 프로파일로 띄우면 **기동을 실패**시킨다
 *     (`KakaoBaseUrlGuard`(#77)·`JwtSecretValidator`(#115)와 같은 fail-fast 선례).
 */
@Tag(name = "Dev Auth", description = "비프로덕션 dev-login (local/dev 전용)")
@RestController
@RequestMapping("/api/v2/auth")
@Profile("local | dev")
class DevAuthController(
    private val authUseCase: AuthUseCase,
    private val environment: Environment,
) {

    @PostConstruct
    fun sealAgainstProd() = DevLoginGuard.assertNotProd(environment.activeProfiles.toList())

    @Operation(
        summary = "dev-login (비프로덕션)",
        description = "역할(CUSTOMER/CARRIER/COORDINATOR/ADMIN)로 결정적 dev 사용자를 get-or-create하고 토큰을 발급한다.",
    )
    @PostMapping("/dev-login")
    fun devLogin(@Valid @RequestBody request: DevLoginRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val role = UserRole.entries.find { it.name == request.role.uppercase() }
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 역할: ${request.role}")
        val tokens = authUseCase.devLogin(role)
        return ResponseEntity.ok(ApiResponse.success(TokenResponse.from(tokens)))
    }
}

/**
 * dev-login이 prod 프로파일과 함께 활성화되는 것을 봉인하는 순수 가드 로직(테스트 용이성을 위해 분리).
 */
object DevLoginGuard {
    const val PROD_PROFILE = "prod"

    fun assertNotProd(activeProfiles: List<String>) {
        check(PROD_PROFILE !in activeProfiles) {
            "dev-login은 prod 프로파일과 함께 활성화될 수 없습니다 (활성 프로파일: $activeProfiles)"
        }
    }
}
