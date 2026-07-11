package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.ReconciliationMismatch

interface ReconciliationMismatchPort {

    /**
     * 미기록 불일치면 적재하고 true, (type, dedupKey) 가 이미 있으면 skip 하고 false.
     * 대사 잡은 윈도를 중첩 스캔하므로 이 멱등성이 중복 적재·중복 알럿을 막는다.
     */
    fun recordIfNew(mismatch: ReconciliationMismatch): Boolean
}
