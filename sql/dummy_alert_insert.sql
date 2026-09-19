-- [임시 더미 데이터] 이상 알림 페이지 / 헤더 종 확인용. 확인 후 sql/dummy_data_delete.sql 로 삭제할 것.
-- (sql/dummy_data_insert.sql 로 넣은 더미 출고 데이터가 먼저 있어야 한다)
--
-- 더미 식별 방법 : ALERT_ID 가 900001 번대
--   - MISMATCH  : 더미 출고 중 ABNORMAL_YN='Y' 인 3건에 대해 미처리(PENDING) 알림 1개씩
--   - LOW_STOCK : 의약품 2번 재고 부족 알림 1개 (반출과 무관하므로 OUTBOUND_ID 는 NULL)
-- 한글은 클라이언트 인코딩 문제를 피하려고 UNISTR(유니코드 이스케이프)로 넣는다.

DECLARE
    v_id     NUMBER := 900000;
    v_diff   NUMBER;
    v_word   VARCHAR2(20);
BEGIN

    FOR r IN (
        SELECT o.OUTBOUND_ID, o.SLIP_ID, o.MEDICINE_ID, o.OUTBOUND_QTY, o.OUTBOUND_TIME, si.REQUEST_QTY
          FROM OUTBOUND o
          JOIN SLIP_ITEM si ON si.SLIP_ID = o.SLIP_ID AND si.MEDICINE_ID = o.MEDICINE_ID
         WHERE o.SLIP_ID LIKE 'DUMMY-%' AND o.ABNORMAL_YN = 'Y'
         ORDER BY o.OUTBOUND_ID
    ) LOOP

        v_id   := v_id + 1;
        v_diff := ABS(r.OUTBOUND_QTY - r.REQUEST_QTY);
        v_word := CASE WHEN r.REQUEST_QTY > r.OUTBOUND_QTY THEN UNISTR('\BD80\C871') ELSE UNISTR('\CD08\ACFC') END; -- 부족 / 초과

        -- 예) 전표 3개 · 객체탐지 4개 · 1개 초과 / 전표 DUMMY-ORD-...
        INSERT INTO ALERT (ALERT_ID, OUTBOUND_ID, ALERT_CONTENT, PROCESS_STATUS, ALERT_TYPE, MEDICINE_ID, ALERT_TIME)
        VALUES (
            v_id, r.OUTBOUND_ID,
            UNISTR('\C804\D45C ') || r.REQUEST_QTY || UNISTR('\AC1C \00B7 \AC1D\CCB4\D0D0\C9C0 ') || r.OUTBOUND_QTY
                || UNISTR('\AC1C \00B7 ') || v_diff || UNISTR('\AC1C ') || v_word
                || UNISTR(' / \C804\D45C ') || r.SLIP_ID,
            'PENDING', 'MISMATCH', r.MEDICINE_ID, r.OUTBOUND_TIME
        );
    END LOOP;

    -- 재고 부족 : 현재 1개 / 최소 3개
    v_id := v_id + 1;

    INSERT INTO ALERT (ALERT_ID, OUTBOUND_ID, ALERT_CONTENT, PROCESS_STATUS, ALERT_TYPE, MEDICINE_ID, ALERT_TIME)
    VALUES (
        v_id, NULL,
        UNISTR('\D604\C7AC 1\AC1C / \CD5C\C18C 3\AC1C'),
        'PENDING', 'LOW_STOCK', 2, TIMESTAMP '2026-09-18 10:30:00'
    );

    COMMIT;
END;
/
