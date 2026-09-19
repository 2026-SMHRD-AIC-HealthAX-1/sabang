package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

// 이 클래스가 DB 테이블과 연결되는 JPA Entity라는 뜻
// 전표 품목 - 전표 1개에 약품이 여러 줄(No.1, 2, 3...) 들어가는 1:N 관계
// DB에는 (SLIP_ID, MEDICINE_ID) UNIQUE 제약이 있고, OUTBOUND가 이 키를 복합 FK로 참조한다.
// (UNIQUE는 DB 스크립트(sql/slip_split_header_item.sql)에서 관리하므로 여기서는 선언하지 않음)
@Entity
// Oracle DB의 SLIP_ITEM 테이블과 연결
@Table(name = "SLIP_ITEM")
public class SlipItem {

    // 복합 기본키 - 전표번호 + 품목번호
    @EmbeddedId
    private SlipItemId id;

    // 외래키(FK) - 전표번호, SLIP 테이블 참조 (기본키의 SLIP_ID와 같은 컬럼)
    @MapsId("slipId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SLIP_ID", nullable = false)
    private Slip slip;

    // 외래키(FK) - 의약품ID, MEDICINE 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEDICINE_ID", nullable = false)
    private Medicine medicine;

    // 요청 수량 (NOT NULL)
    @Column(name = "REQUEST_QTY", nullable = false)
    private Long requestQty;

    // 기본 생성자 (JPA는 파라미터 없는 생성자가 필수예요)
    public SlipItem() {
    }

    // Getter / Setter

    public SlipItemId getId() {
        return id;
    }

    public void setId(SlipItemId id) {
        this.id = id;
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

    public Long getRequestQty() {
        return requestQty;
    }

    public void setRequestQty(Long requestQty) {
        this.requestQty = requestQty;
    }
}
