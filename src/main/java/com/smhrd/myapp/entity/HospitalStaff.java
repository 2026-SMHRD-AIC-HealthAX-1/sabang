package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity

@Table(name = "HOSPITAL_STAFF")
public class HospitalStaff {
	
	// 이력번호
    // HOSPITAL_STAFF 테이블의 기본키(PK)
    // DB : HISTORY_ID NUMBER(10) PRIMARY KEY
    @Id
    @Column(name = "HISTORY_ID")
    private Long historyId;


    // 관리자 ID
    // HOSPITAL_STAFF.ADMIN_ID가
    // MEMBER.MEMBER_ID를 참조하는 Foreign Key
    @ManyToOne
    @JoinColumn(
        name = "ADMIN_ID",
        referencedColumnName = "MEMBER_ID"
    )
    private Member admin;


    // 직원 ID
    // HOSPITAL_STAFF.STAFF_ID가
    // MEMBER.MEMBER_ID를 참조하는 Foreign Key
    @ManyToOne
    @JoinColumn(
        name = "STAFF_ID",
        referencedColumnName = "MEMBER_ID"
    )
    private Member staff;

    
    // Getter / Setter

    // 이력번호 가져오기
    public Long getHistoryId() {
        return historyId;
    }

    // 이력번호 저장/변경
    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    // 관리자 정보 가져오기
    public Member getAdmin() {
        return admin;
    }

    // 관리자 정보 저장/변경
    public void setAdmin(Member admin) {
        this.admin = admin;
    }

    // 직원 정보 가져오기
    public Member getStaff() {
        return staff;
    }

    // 직원 정보 저장/변경
    public void setStaff(Member staff) {
        this.staff = staff;
    }

}
