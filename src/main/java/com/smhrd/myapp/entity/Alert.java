package com.smhrd.myapp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 이 클래스가 DB 테이블과 연결되는 JPA Entity라는 뜻
// 이상 알림 - 수량 불일치(MISMATCH, 반출 기반)와 재고 부족(LOW_STOCK, 카메라 기반)을 함께 담는다
@Entity
// Oracle DB의 ALERT 테이블과 연결
@Table(name = "ALERT")
public class Alert {

    // 알림 종류 - 전표·세그먼트 수량 불일치
    public static final String TYPE_MISMATCH = "MISMATCH";

    // 알림 종류 - 카메라로 확인한 재고가 최소 수량 이하
    public static final String TYPE_LOW_STOCK = "LOW_STOCK";

    // 처리 상태 - 관리자가 아직 확인하지 않음
    public static final String STATUS_PENDING = "PENDING";

    // 처리 상태 - 관리자가 확인 처리함
    public static final String STATUS_DONE = "DONE";

    // 기본키(PK) 지정 - 알림ID
    // IDENTITY 자동증가는 캠퍼스 오라클 DB 버전에서 지원 안 될 수 있어 제외 (필요 시 회의 후 재도입)
    @Id
    @Column(name = "ALERT_ID")
    private Long alertId;

    // 외래키(FK) - 반출ID, OUTBOUND 테이블 참조 (NULL 허용: 재고 부족 알림은 반출과 무관)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OUTBOUND_ID")
    private Outbound outbound;

    // 외래키(FK) - 의약품ID, MEDICINE 테이블 참조 (NOT NULL)
    // 알림이 어느 병원(관리자)의 것인지는 의약품 -> 카메라 -> 관리자로 구분한다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEDICINE_ID", nullable = false)
    private Medicine medicine;

    // 알림 종류 (NOT NULL) - MISMATCH / LOW_STOCK
    @Column(name = "ALERT_TYPE", length = 20, nullable = false)
    private String alertType;

    // 알림내용 (NOT NULL, 최대 500자)
    @Column(name = "ALERT_CONTENT", length = 500, nullable = false)
    private String alertContent;

    // 알림 발생 시각 (NOT NULL)
    @Column(name = "ALERT_TIME", nullable = false)
    private LocalDateTime alertTime;

    // 처리상태 (NOT NULL, 최대 20자) - PENDING(미처리) / DONE(관리자 확인 처리)
    @Column(name = "PROCESS_STATUS", length = 20, nullable = false)
    private String processStatus = STATUS_PENDING;

    // 기본 생성자 (JPA는 파라미터 없는 생성자가 필수예요)
    public Alert() {
    }

    // Getter / Setter

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Outbound getOutbound() {
        return outbound;
    }

    public void setOutbound(Outbound outbound) {
        this.outbound = outbound;
    }

    public Medicine getMedicine() {
        return medicine;
    }

    public void setMedicine(Medicine medicine) {
        this.medicine = medicine;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getAlertContent() {
        return alertContent;
    }

    public void setAlertContent(String alertContent) {
        this.alertContent = alertContent;
    }

    public LocalDateTime getAlertTime() {
        return alertTime;
    }

    public void setAlertTime(LocalDateTime alertTime) {
        this.alertTime = alertTime;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }
}
