-- WARD.ADMIN_ID : 병동을 등록한 관리자(=병원). 지금까지는 병동이 병원 구분 없이 전체 공유였는데,
-- 병원마다 병동을 따로 관리하도록 구분한다.
-- 실행 전 확인: 기존 병동(7병동, DUMMY-3, DUMMY-5, DUMMY-ICU) 전부 OUTBOUND->MEDICINE 기준으로
-- 전부 admin 소속인 것을 확인함 (admin2가 쓴 병동은 없음)

ALTER TABLE WARD ADD (ADMIN_ID VARCHAR2(200));

-- 기존 병동: 이 병동으로 나간 출고 기록의 의약품 관리자로 채움
UPDATE WARD w
   SET w.ADMIN_ID = (
        SELECT MIN(m.ADMIN_ID)
          FROM OUTBOUND o
          JOIN MEDICINE m ON m.MEDICINE_ID = o.MEDICINE_ID
         WHERE o.WARD_ID = w.WARD_ID
   );

COMMIT;

ALTER TABLE WARD MODIFY (ADMIN_ID NOT NULL);

ALTER TABLE WARD ADD CONSTRAINT FK_WARD_ADMIN FOREIGN KEY (ADMIN_ID) REFERENCES MEMBER (MEMBER_ID);
