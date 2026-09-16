package com.smhrd.myapp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 이 클래스가 DB 테이블과 연결되는 JPA Entity라는 뜻
@Entity
// Oracle DB의 OUTBOUND 테이블과 연결
@Table(name = "OUTBOUND")
public class Outbound {

    // 기본키(PK) 지정 - 반출ID
    @Id
    @Column(name = "OUTBOUND_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long outboundId;

    // 외래키(FK) - 전표번호, SLIP 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SLIP_ID", nullable = false)
    private Slip slip;

    // 외래키(FK) - 의약품ID, MEDICINE 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEDICINE_ID", nullable = false)
    private Medicine medicine;

    // 외래키(FK) - 병동번호, WARD 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WARD_ID", nullable = false)
    private Ward ward;

    // 출고수량 (NOT NULL)
    @Column(name = "OUTBOUND_QTY", nullable = false)
    private Long outboundQty;

    // 반출시간 (NULL 허용) - LocalDateTime 사용 (java.util.Date + @Temporal은 JPA 3.2부터 deprecated)
    @Column(name = "OUTBOUND_TIME")
    private LocalDateTime outboundTime;

    // 이상발생여부 - 정상/이상 여부 (Y 또는 N, NULL 허용)
    @Column(name = "ABNORMAL_YN", length = 1)
    private String abnormalYn;

    // 기본 생성자 (JPA는 파라미터 없는 생성자가 필수예요)
    public Outbound() {
    }

    // Getter / Setter

    public Long getOutboundId() {
        return outboundId;
    }

    public void setOutboundId(Long outboundId) {
        this.outboundId = outboundId;
    }

    public Slip getSlip() {
        return slip;
    }

    public void setSlip(Slip slip) {
        this.slip = slip;
    }

    public Medicine getMedicine() {
        return medicine;
    }

    public void setMedicine(Medicine medicine) {
        this.medicine = medicine;
    }

    public Ward getWard() {
        return ward;
    }

    public void setWard(Ward ward) {
        this.ward = ward;
    }

    public Long getOutboundQty() {
        return outboundQty;
    }

    public void setOutboundQty(Long outboundQty) {
        this.outboundQty = outboundQty;
    }

    public LocalDateTime getOutboundTime() {
        return outboundTime;
    }

    public void setOutboundTime(LocalDateTime outboundTime) {
        this.outboundTime = outboundTime;
    }

    public String getAbnormalYn() {
        return abnormalYn;
    }

    public void setAbnormalYn(String abnormalYn) {
        this.abnormalYn = abnormalYn;
    }
}