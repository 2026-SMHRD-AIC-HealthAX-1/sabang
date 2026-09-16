package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 이 클래스가 DB 테이블과 연결되는 JPA Entity라는 뜻
@Entity
// Oracle DB의 SLIP 테이블과 연결
@Table(name = "SLIP")
public class Slip {

    // 기본키(PK) 지정 - 전표번호
    // IDENTITY 자동증가는 캠퍼스 오라클 DB 버전에서 지원 안 될 수 있어 제외 (필요 시 회의 후 재도입)
    @Id
    @Column(name = "SLIP_ID")
    private Long slipId;

    // 외래키(FK) - 병동번호, WARD 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WARD_ID", nullable = false)
    private Ward ward;

    // 수량 (NOT NULL) - 요청 수량
    @Column(name = "REQUEST_QTY", nullable = false)
    private Long requestQty;

    // 요청 담당자 이름 (NOT NULL, 최대 50자)
    @Column(name = "REQUESTER_NAME", length = 50, nullable = false)
    private String requesterName;

    // 기본 생성자 (JPA는 파라미터 없는 생성자가 필수예요)
    public Slip() {
    }

    // Getter / Setter

    public Long getSlipId() {
        return slipId;
    }

    public void setSlipId(Long slipId) {
        this.slipId = slipId;
    }

    public Ward getWard() {
        return ward;
    }

    public void setWard(Ward ward) {
        this.ward = ward;
    }

    public Long getRequestQty() {
        return requestQty;
    }

    public void setRequestQty(Long requestQty) {
        this.requestQty = requestQty;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }
}