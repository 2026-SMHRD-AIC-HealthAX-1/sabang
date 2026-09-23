-- 알림소리(SOUND) 기능 제거 (2026-09-23 팀 결정)
-- SOUND 테이블과 MEMBER.SOUND_ID(알림소리 참조)를 없앤다. 데이터 0건, 사용하는 코드 없음.
--
-- ⚠ 실행 순서 주의: spring.jpa.hibernate.ddl-auto=update 라서, Sound 엔티티가 남아있는 구버전 코드로
--   서버를 켜면 Hibernate가 SOUND 테이블/SOUND_ID 컬럼을 다시 만들어버린다.
--   → Member/Sound 엔티티 제거 커밋을 팀원 전원이 pull한 "뒤에" 이 스크립트를 실행할 것.

-- 1) MEMBER -> SOUND FK 제약 (Hibernate가 자동 생성한 이름)
ALTER TABLE MEMBER DROP CONSTRAINT FK3LFNLMQP3A2B1FOVAAMX8TSVO;

-- 2) MEMBER의 알림소리 참조 컬럼
ALTER TABLE MEMBER DROP COLUMN SOUND_ID;

-- 3) 알림소리 테이블
DROP TABLE SOUND;

COMMIT;
