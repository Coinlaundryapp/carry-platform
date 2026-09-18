package com.carry.payment.application.port.outbound

import com.carry.payment.domain.model.LedgerEntry
import com.carry.payment.domain.vo.LedgerAccountType

interface LedgerPort {

    /** 거래 그룹의 기입 행들을 append 한다(수정·삭제 경로 없음). 결제/환불 확정과 동일 트랜잭션에서 호출된다. */
    fun record(entries: List<LedgerEntry>)

    /** 계정 잔액 = 해당 계정 행들의 부호 합. PLATFORM 처럼 단일 주체면 accountId=null. */
    fun balance(accountType: LedgerAccountType, accountId: Long?): Long
}
