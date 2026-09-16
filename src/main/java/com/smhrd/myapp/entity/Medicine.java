package com.smhrd.myapp.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
	
}
