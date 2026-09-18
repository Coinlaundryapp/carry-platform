package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * 수거 시한이 임박하도록 PENDING 으로 남은 배차를 TIMEOUT 으로 종결하는 안전망 스위퍼.
 *
 * 배차가 아무도 잡지 않은 채 방치되면 좀비 배차가 되고, 고객은 주문이 멈춘 채로 남는다.
 * 만료 배차를 주기적으로 TIMEOUT 처리하면 [DispatchTimeoutEvent] 가 발행돼 Order 사가가
 * 후속(재배차/취소)을 이어갈 수 있고, `carry.dispatch.timeout` 메트릭으로 운영 가시성이 생긴다.
 *
 * 멀티 인스턴스 환경에선 @SchedulerLock 으로 매 주기 한 노드만 실행한다(ShedLock).
 * 락이 풀려 중복 실행되더라도 [DispatchCommandUseCase.timeoutDispatch] 의 도메인 상태 가드로 멱등하다.
 *
 * 만료 리드타임(`carry.dispatch.pickup-timeout-lead-minutes`, 기본 30분)은 **여기 한 곳에서만** 읽고,
 * 조회 프리필터와 도메인 판정에 같은 값을 넘긴다. 예전에는 도메인(`Dispatch.isExpired` 의 상수 30분)과
 * 조회 SQL(`INTERVAL '30 minutes'`)에 값이 이중화돼 한쪽만 바뀌면 어긋날 수 있었다.
 */
@Component
class DispatchTimeoutSweeper(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val dispatchCommandUseCase: DispatchCommandUseCase,
    private val clock: Clock,
    @Value("\${carry.dispatch.pickup-timeout-lead-minutes:30}") private val leadMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.dispatch.timeout-sweep-interval-ms:300000}")
    @SchedulerLock(name = "dispatchTimeoutSweep", lockAtMostFor = "PT4M", lockAtLeastFor = "PT0S")
    fun sweepExpiredDispatches() {
        val now = clock.instant()
        val lead = Duration.ofMinutes(leadMinutes)
        // 조회는 인덱스를 태우기 위한 프리필터이고, 만료 여부의 최종 판정은 도메인이 한다.
        // 두 경계가 같은 lead 를 쓰므로 프리필터가 판정을 앞질러 누락시키지 않는다.
        val expired = dispatchPersistencePort.findExpiredPendingDispatches(now.plus(lead))
            .filter { it.isExpired(now, lead) }
        if (expired.isEmpty()) return

        log.info("배차 타임아웃 스위퍼: 만료 PENDING 배차 {}건 종결 시작", expired.size)
        expired.forEach { dispatch ->
            try {
                dispatchCommandUseCase.timeoutDispatch(dispatch.id!!)
            } catch (e: Exception) {
                // 멀티 인스턴스 레이스 등으로 이미 종결된 배차는 건너뛰고 배치를 계속한다.
                log.warn("배차 타임아웃 스위퍼: 배차 {} 종결 실패(이미 종결됐을 수 있음) — {}", dispatch.id, e.message)
            }
        }
    }
}
