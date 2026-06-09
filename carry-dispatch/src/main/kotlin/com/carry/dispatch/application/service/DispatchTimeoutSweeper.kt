package com.carry.dispatch.application.service

import com.carry.dispatch.application.port.inbound.DispatchCommandUseCase
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 수거 시한이 임박하도록 PENDING 으로 남은 배차를 TIMEOUT 으로 종결하는 안전망 스위퍼.
 *
 * 배차가 아무도 잡지 않은 채 방치되면 좀비 배차가 되고, 고객은 주문이 멈춘 채로 남는다.
 * 만료 배차를 주기적으로 TIMEOUT 처리하면 [DispatchTimeoutEvent] 가 발행돼 Order 사가가
 * 후속(재배차/취소)을 이어갈 수 있고, `carry.dispatch.timeout` 메트릭으로 운영 가시성이 생긴다.
 *
 * 멀티 인스턴스 환경에선 @SchedulerLock 으로 매 주기 한 노드만 실행한다(ShedLock).
 * 락이 풀려 중복 실행되더라도 [DispatchCommandUseCase.timeoutDispatch] 의 도메인 상태 가드로 멱등하다.
 */
@Component
class DispatchTimeoutSweeper(
    private val dispatchPersistencePort: DispatchPersistencePort,
    private val dispatchCommandUseCase: DispatchCommandUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "\${carry.dispatch.timeout-sweep-interval-ms:300000}")
    @SchedulerLock(name = "dispatchTimeoutSweep", lockAtMostFor = "PT4M", lockAtLeastFor = "PT0S")
    fun sweepExpiredDispatches() {
        val expired = dispatchPersistencePort.findExpiredPendingDispatches()
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
