-- [임시 더미 데이터] 통계 화면 동작 확인용. 확인 후 sql/dummy_data_delete.sql 로 전부 삭제할 것.
--
-- 더미 식별 방법 (삭제할 때 이 규칙으로 찾음)
--   WARD      : WARD_ID   가 'DUMMY-' 로 시작 (병동명 끝에 "(더미)")
--   SLIP      : SLIP_ID   가 'DUMMY-' 로 시작
--   SLIP_ITEM : SLIP_ID   가 'DUMMY-' 로 시작
--   OUTBOUND  : SLIP_ID   가 'DUMMY-' 로 시작 (OUTBOUND_ID 는 900001 번대)
-- 의약품은 새로 만들지 않고 기존 MEDICINE 1, 2번을 그대로 사용한다.
-- 병동은 기존 병동 1개(7병동) + 더미 병동 3개로 4개 병동에 분산한다.
-- 한글은 클라이언트 인코딩 문제를 피하려고 UNISTR(유니코드 이스케이프)로 넣는다.

DECLARE
    v_real_ward  WARD.WARD_ID%TYPE;
    v_ward       WARD.WARD_ID%TYPE;
    v_slip       SLIP.SLIP_ID%TYPE;
    v_date       DATE;
    v_qty        NUMBER;
    v_out_id     NUMBER := 900000;
    v_item_no    NUMBER;
BEGIN
    SELECT MIN(WARD_ID) INTO v_real_ward FROM WARD WHERE WARD_ID NOT LIKE 'DUMMY-%';

    -- 더미 병동 3개 : 3병동(더미), 5병동(더미), ICU(더미)
    INSERT INTO WARD (WARD_ID, WARD_NAME, LOCATION)
    VALUES ('DUMMY-3', UNISTR('3\BCD1\B3D9(\B354\BBF8)'), 'DUMMY');
    INSERT INTO WARD (WARD_ID, WARD_NAME, LOCATION)
    VALUES ('DUMMY-5', UNISTR('5\BCD1\B3D9(\B354\BBF8)'), 'DUMMY');
    INSERT INTO WARD (WARD_ID, WARD_NAME, LOCATION)
    VALUES ('DUMMY-ICU', UNISTR('ICU(\B354\BBF8)'), 'DUMMY');

    -- 전표 12장 : 2026-07-06 부터 6일 간격 (7월~9월, 여러 주/월에 걸치게)
    FOR i IN 0..11 LOOP

        v_date := DATE '2026-07-06' + (i * 6);
        v_slip := 'DUMMY-ORD-' || TO_CHAR(v_date, 'YYYYMMDD') || '-' || LPAD(i + 1, 3, '0');

        v_ward := CASE MOD(i, 4)
                      WHEN 0 THEN v_real_ward
                      WHEN 1 THEN 'DUMMY-3'
                      WHEN 2 THEN 'DUMMY-5'
                      ELSE 'DUMMY-ICU'
                  END;

        INSERT INTO SLIP (SLIP_ID, WARD_ID, REQUESTER_NAME, SLIP_DATE)
        VALUES (v_slip, v_ward, 'DUMMY-NURSE', v_date);

        -- 품목 : 1번 의약품은 모든 전표에, 2번 의약품은 짝수 전표에만 (전표 1 : 품목 1~2)
        FOR m IN 1..2 LOOP

            IF m = 1 OR MOD(i, 2) = 0 THEN

                v_item_no := m;
                v_qty := 1 + MOD(i + m, 3);

                INSERT INTO SLIP_ITEM (SLIP_ID, ITEM_NO, MEDICINE_ID, REQUEST_QTY)
                VALUES (v_slip, v_item_no, m, v_qty);

                -- 5번째마다 요청보다 1개 더 나간 이상 건으로 만든다
                v_out_id := v_out_id + 1;

                INSERT INTO OUTBOUND (OUTBOUND_ID, SLIP_ID, MEDICINE_ID, WARD_ID, OUTBOUND_QTY, OUTBOUND_TIME, ABNORMAL_YN)
                VALUES (
                    v_out_id, v_slip, m, v_ward,
                    CASE WHEN MOD(i, 5) = 4 THEN v_qty + 1 ELSE v_qty END,
                    v_date + (8 + MOD(i * 3 + m, 10)) / 24,
                    CASE WHEN MOD(i, 5) = 4 THEN 'Y' ELSE 'N' END
                );
            END IF;
        END LOOP;
    END LOOP;

    COMMIT;
END;
/
