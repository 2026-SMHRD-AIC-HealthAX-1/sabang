package com.smhrd.myapp.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 이 클래스가 DB 테이블과 연결되는 JPA Entity라는 뜻
// 전표 헤더 (전표번호, 병동, 요청자, 일자). 약품 줄(No.1, 2, 3...)은 SlipItem이 담당
@Entity
// Oracle DB의 SLIP 테이블과 연결
@Table(name = "SLIP")
public class Slip {

    // 기본키(PK) 지정 - 전표번호 (예: ORD-20250910-002)
    @Id
    @Column(name = "SLIP_ID", length = 100)
    private String slipId;

    // 외래키(FK) - 병동번호, WARD 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "WARD_ID", nullable = false)
    private Ward ward;

    // 요청 담당자 이름 (NOT NULL, 최대 50자)
    @Column(name = "REQUESTER_NAME", length = 50, nullable = false)
    private String requesterName;

    // 전표 일자 (NULL 허용)
    @Column(name = "SLIP_DATE")
    private LocalDate slipDate;

    // 스캔한 전표 원본 이미지 경로 (전표 상세 화면에서 원본 이미지를 보여줄 때 사용, NULL 허용)
    @Column(name = "IMAGE_PATH", length = 255)
    private String imagePath;

    // 생성 시각 (전표목록에서 "가장 최신 전표"를 정확히 가리기 위함 - SLIP_DATE는 날짜만이라 같은 날짜끼리는 순서를 못 가림)
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    // 기본 생성자 (JPA는 파라미터 없는 생성자가 필수예요)
    public Slip() {
    }

    // Getter / Setter

    public String getSlipId() {
        return slipId;
    }

    public void setSlipId(String slipId) {
        this.slipId = slipId;
    }

    public Ward getWard() {
        return ward;
    }

    public void setWard(Ward ward) {
        this.ward = ward;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public LocalDate getSlipDate() {
        return slipDate;
    }

    public void setSlipDate(LocalDate slipDate) {
        this.slipDate = slipDate;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
