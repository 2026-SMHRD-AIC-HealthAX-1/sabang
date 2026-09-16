package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

//이 클래스가 DB 테이블과 연결되는 JPA Entity라는 뜻
@Entity
//Oracle DB의 SOUND 테이블과 연결
@Table(name = "SOUND")
public class SOUND {

	    // 기본키(PK) 지정
	    @Id
	    
	    // DB의 SOUND_ID 컬럼과 연결
	    @Column(name = "SOUND_ID")
	    private Long soundId;

	    // DB의 SOUND_FILE 컬럼과 연결
	    // nullable = false → NOT NULL
	    // length = 255 → VARCHAR2(255)에 해당
	    @Column(name = "SOUND_FILE", nullable = false, length = 255)
	    private String soundFile;

	    // soundId 값을 가져오는 메소드
	    public Long getSoundId() {
	        return soundId;
	    }

	    // soundId 값을 저장/변경하는 메소드	
	    public void setSoundId(Long soundId) {
	        this.soundId = soundId;
	    }

	    // soundFile 값을 가져오는 메소드
	    public String getSoundFile() {
	        return soundFile;
	    }
	    // soundFile 값을 저장/변경하는 메소드
	    public void setSoundFile(String soundFile) {
	        this.soundFile = soundFile;
	    }
}
