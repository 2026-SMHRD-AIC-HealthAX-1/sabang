package com.smhrd.myapp.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity

@Table(name = "MEDICINE")
public class Medicine {

	// 의약품 ID
    // MEDICINE 테이블의 기본키(PK)
    // DB : MEDICINE_ID NUMBER(10) PRIMARY KEY
    @Id
    @Column(name = "MEDICINE_ID")
    private Long medicineId;


    // 의약품명
    // DB : MEDICINE_NAME VARCHAR2(100) NOT NULL
    @Column(name = "MEDICINE_NAME", nullable = false, length = 100)
    private String medicineName;


    // 고위험군 여부
    // Y : 고위험 의약품
    // N : 일반 의약품
    // DB : HIGH_RISK_YN VARCHAR2(1) NOT NULL
    @Column(name = "HIGH_RISK_YN", nullable = false, length = 1)
    private String highRiskYn;


    // 제조사
    // DB : MANUFACTURER VARCHAR2(100) NOT NULL
    @Column(name = "MANUFACTURER", nullable = false, length = 100)
    private String manufacturer;


    // 시스템 등록 날짜
    // DB : REGISTER_DATE DATE
    @Column(name = "REGISTER_DATE")
    private LocalDate registerDate;


    // 의약품을 등록한 관리자(=병원) - 병원 구분은 카메라를 거치지 않고 이 값으로 한다
    // DB : ADMIN_ID VARCHAR2(200) NOT NULL, MEMBER 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ADMIN_ID", nullable = false)
    private Member admin;


    // 구역(Zone) 지정 - 어느 카메라 화면에 이 의약품 구역이 있는지 (NULL 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CAMERA_ID")
    private Camera camera;

    // 구역 위치/크기 - 카메라 화면 대비 %(0~100)로 저장 (해상도 달라져도 안 깨지게)
    @Column(name = "REGION_X")
    private Double regionX;

    @Column(name = "REGION_Y")
    private Double regionY;

    @Column(name = "REGION_WIDTH")
    private Double regionWidth;

    @Column(name = "REGION_HEIGHT")
    private Double regionHeight;

    // 재고 부족 알림 기준 최소 수량 - 관리자가 약품 추가/수정 화면에서 입력 (NULL 허용)
    // 값이 없으면 이 의약품은 재고 부족 알림을 만들지 않는다
    // DB : MIN_QTY NUMBER(10)
    @Column(name = "MIN_QTY")
    private Long minQty;


    // Getter / Setter

    public Long getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(Long medicineId) {
        this.medicineId = medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getHighRiskYn() {
        return highRiskYn;
    }

    public void setHighRiskYn(String highRiskYn) {
        this.highRiskYn = highRiskYn;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public LocalDate getRegisterDate() {
        return registerDate;
    }

    public void setRegisterDate(LocalDate registerDate) {
        this.registerDate = registerDate;
    }

    public Member getAdmin() {
        return admin;
    }

    public void setAdmin(Member admin) {
        this.admin = admin;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public Double getRegionX() {
        return regionX;
    }

    public void setRegionX(Double regionX) {
        this.regionX = regionX;
    }

    public Double getRegionY() {
        return regionY;
    }

    public void setRegionY(Double regionY) {
        this.regionY = regionY;
    }

    public Double getRegionWidth() {
        return regionWidth;
    }

    public void setRegionWidth(Double regionWidth) {
        this.regionWidth = regionWidth;
    }

    public Double getRegionHeight() {
        return regionHeight;
    }

    public void setRegionHeight(Double regionHeight) {
        this.regionHeight = regionHeight;
    }

    public Long getMinQty() {
        return minQty;
    }

    public void setMinQty(Long minQty) {
        this.minQty = minQty;
    }

}
