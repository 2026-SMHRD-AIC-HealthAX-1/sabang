package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity

@Table(name = "WARD")
public class Ward {
	
	 // 병동번호
    // WARD 테이블의 기본키(PK)
    // DB : WARD_ID VARCHAR2(100) PRIMARY KEY (예: '7병동')
    @Id
    @Column(name = "WARD_ID", length = 100)
    private String wardId;


    // 병동명
    // DB : WARD_NAME VARCHAR2(50) NOT NULL
    @Column(name = "WARD_NAME", nullable = false, length = 50)
    private String wardName;


    // 병동 위치
    // DB : LOCATION VARCHAR2(100)
    @Column(name = "LOCATION", length = 100)
    private String location;


    // Getter / Setter
    
    // 병동번호 가져오기
    public String getWardId() {
        return wardId;
    }

    // 병동번호 저장/변경
    public void setWardId(String wardId) {
        this.wardId = wardId;
    }

    // 병동명 가져오기
    public String getWardName() {
        return wardName;
    }

    // 병동명 저장/변경
    public void setWardName(String wardName) {
        this.wardName = wardName;
    }

    // 병동 위치 가져오기
    public String getLocation() {
        return location;
    }

    // 병동 위치 저장/변경
    public void setLocation(String location) {
        this.location = location;
    }

}
