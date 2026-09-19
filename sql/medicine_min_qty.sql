-- MEDICINE.MIN_QTY : 재고 부족 알림 기준이 되는 최소 수량 (관리자가 약품 추가/수정 화면에서 입력)
--
-- NULL 허용: 값이 없는 약품은 재고 부족 알림을 만들지 않는다. (기존 약품 2개도 NULL 로 둔다)
-- 입력을 필수로 할지는 팀 결정 후 화면/API 검사에서만 바꾸면 되므로 DB 는 NULL 을 허용해 둔다.

ALTER TABLE MEDICINE ADD (MIN_QTY NUMBER(10));

ALTER TABLE MEDICINE ADD CONSTRAINT CK_MEDICINE_MIN_QTY CHECK (MIN_QTY IS NULL OR MIN_QTY >= 0);
