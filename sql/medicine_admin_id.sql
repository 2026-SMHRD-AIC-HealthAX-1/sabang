-- MEDICINE.ADMIN_ID : 의약품을 등록한 관리자(=병원). 병원 구분을 카메라 경유(MEDICINE -> CAMERA -> ADMIN)가 아니라
-- 의약품이 직접 가진다. 카메라가 없는 의약품도 주인을 알 수 있고, 조회/권한 검사가 단순해진다.
-- 실행 전 카메라가 없는 의약품이 0건인 것을 확인함 (기존 의약품은 카메라의 관리자 값으로 채울 수 있음)

ALTER TABLE MEDICINE ADD (ADMIN_ID VARCHAR2(200));

-- 기존 의약품: 연결된 카메라의 관리자로 채움
UPDATE MEDICINE m
   SET m.ADMIN_ID = (SELECT c.ADMIN_ID FROM CAMERA c WHERE c.CAMERA_ID = m.CAMERA_ID);

COMMIT;

ALTER TABLE MEDICINE MODIFY (ADMIN_ID NOT NULL);

ALTER TABLE MEDICINE ADD CONSTRAINT FK_MEDICINE_ADMIN FOREIGN KEY (ADMIN_ID) REFERENCES MEMBER (MEMBER_ID);
