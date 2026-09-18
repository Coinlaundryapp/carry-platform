package com.carry.app.auth

import com.carry.user.adapter.outbound.auth.RedisRefreshTokenStore
import com.carry.user.application.port.outbound.RotateResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * 실 Redis(Testcontainers)에 대한 [RedisRefreshTokenStore]의 Lua 회전 검증.
 * [com.carry.user.adapter.outbound.auth.InMemoryRefreshTokenStore] 단위테스트와 **동일 시나리오**를
 * 실제 Lua로 재현해 의미 동등성을 보장한다(spec §8 parity).
 *
 * Spring 컨텍스트(test 프로파일은 Redis 제외)를 띄우지 않고 StringRedisTemplate을 직접 구성한다.
 * 시계 의존(grace 만료)은 결정성을 위해 InMemory 단위테스트가 담당하고, 여기선 grace를 충분히 크게
 * 둬 wall-clock 대기 없이 분기만 결정적으로 확인한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisRefreshTokenStoreIntegrationTest {

    private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)

    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var template: StringRedisTemplate
    private lateinit var sut: RedisRefreshTokenStore

    @BeforeAll
    fun startContainer() {
        redis.start()
        connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
        connectionFactory.afterPropertiesSet()
        template = StringRedisTemplate(connectionFactory)
        template.afterPropertiesSet()
        sut = RedisRefreshTokenStore(template, ttlMillis = 600_000, graceMillis = 60_000)
    }

    @AfterAll
    fun stopContainer() {
        connectionFactory.destroy()
        redis.stop()
    }

    @BeforeEach
    fun flush() {
        template.execute { it.serverCommands().flushAll(); null }
    }

    private fun newSession() = UUID.randomUUID().toString()

    @Test
    fun `현재 jti로 회전하면 ROTATED`() {
        val s = newSession()
        sut.start(s, "j1")

        assertThat(sut.rotate(s, "j1", "j2")).isEqualTo(RotateResult.ROTATED)
    }

    @Test
    fun `없는 세션은 ABSENT`() {
        assertThat(sut.rotate(newSession(), "j1", "j2")).isEqualTo(RotateResult.ABSENT)
    }

    @Test
    fun `알 수 없는 옛 jti 재사용은 REUSE이고 세션을 폐기한다`() {
        val s = newSession()
        sut.start(s, "j1")
        sut.rotate(s, "j1", "j2") // cur=j2, prev=j1

        assertThat(sut.rotate(s, "unknown-old-jti", "j3")).isEqualTo(RotateResult.REUSE)
        // 폐기 확인 — 현재 jti로도 더 이상 회전 불가
        assertThat(sut.rotate(s, "j2", "j4")).isEqualTo(RotateResult.ABSENT)
    }

    @Test
    fun `유예 내 직전 jti 재시도는 ROTATED`() {
        val s = newSession()
        sut.start(s, "j1")
        sut.rotate(s, "j1", "j2") // prev=j1, 방금

        assertThat(sut.rotate(s, "j1", "j3")).isEqualTo(RotateResult.ROTATED)
    }

    @Test
    fun `덮인 cur를 유예 내 제시해도 ROTATED (동시 2요청 회귀)`() {
        val s = newSession()
        sut.start(s, "j1")
        sut.rotate(s, "j1", "j2")  // prev=j1, cur=j2
        sut.rotate(s, "j1", "j3")  // 유예 재시도: prev←j2, cur=j3

        assertThat(sut.rotate(s, "j2", "j4")).isEqualTo(RotateResult.ROTATED)
    }

    @Test
    fun `delete 후 회전은 ABSENT`() {
        val s = newSession()
        sut.start(s, "j1")
        sut.delete(s)

        assertThat(sut.rotate(s, "j1", "j2")).isEqualTo(RotateResult.ABSENT)
    }
}
