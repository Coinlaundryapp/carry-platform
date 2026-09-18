-- 정산 원장 append-only 를 DB 로 강제한다.
--
-- 지금까지 append-only 는 "어댑터가 saveAll 만 호출한다" 는 관례에만 의존했다. 엔티티는 전 필드 val 이고
-- 수정 메서드도 없지만, 리포지토리가 JpaRepository 를 상속해 save/delete* 가 열려 있었고 V25 에는 아무런
-- 차단 장치가 없었다(불변식 카탈로그 §6.2-4). 금액은 되돌릴 수 없고 사후 감사 대상이므로,
-- 역분개(새 행 추가) 외의 경로는 DB 에서 막는다.
--
-- TRUNCATE 는 막지 않는다 — 행 트리거는 TRUNCATE 에 반응하지 않으며, 통합 테스트의 격리(teardown)가
-- 그 경로를 쓴다. 운영 계정에서 TRUNCATE 를 막는 것은 권한 설계의 몫이다.
CREATE OR REPLACE FUNCTION payment_ledger_entries_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '정산 원장은 append-only 입니다 (시도: %)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON payment_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION payment_ledger_entries_append_only();
