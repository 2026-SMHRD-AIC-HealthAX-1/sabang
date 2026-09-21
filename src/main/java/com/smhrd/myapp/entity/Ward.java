package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity

@Table(name = "WARD")
public class Ward {

	 // 병동 대체키(PK) - WARD_CODE(병동번호)가 병원마다 겹칠 수 있어서
	 // 진짜 PK는 이 숫자 대체키를 쓰고, WARD_CODE는 (ADMIN_ID, WARD_CODE)로만 유일하면 된다.
	 // IDENTITY 자동증가는 캠퍼스 오라클 DB 버전에서 지원 안 될 수 있어 제외 (findMaxId()+1로 직접 계산)
    // DB : WARD_SEQ_ID NUMBER(10) PRIMARY KEY
    @Id
    @Column(name = "WARD_SEQ_ID")
    private Long wardSeqId;


    // 병동번호(코드) - 예: '7병동'. 병원(admin) 안에서만 유일하면 됨
    // DB : WARD_CODE VARCHAR2(100) NOT NULL
    @Column(name = "WARD_CODE", nullable = false, length = 100)
    private String wardCode;


    // 병동명
    // DB : WARD_NAME VARCHAR2(50) NOT NULL
    @Column(name = "WARD_NAME", nullable = false, length = 50)
    private String wardName;


    // 병동 위치
    // DB : LOCATION VARCHAR2(100)
    @Column(name = "LOCATION", length = 100)
    private String location;


    // 이 병동을 등록한 관리자(=병원) - 병원별로 병동을 구분한다
    // DB : ADMIN_ID VARCHAR2(200) NOT NULL, MEMBER 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ADMIN_ID", nullable = false)
    private Member admin;


    // Getter / Setter

    public Long getWardSeqId() {
        return wardSeqId;
    }

    public void setWardSeqId(Long wardSeqId) {
        this.wardSeqId = wardSeqId;
    }

    public String getWardCode() {
        return wardCode;
    }

    public void setWardCode(String wardCode) {
        this.wardCode = wardCode;
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

    // 관리자 정보 가져오기
    public Member getAdmin() {
        return admin;
    }

    // 관리자 정보 저장/변경
    public void setAdmin(Member admin) {
        this.admin = admin;
    }

}
