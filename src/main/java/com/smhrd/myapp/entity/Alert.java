package com.smhrd.myapp.entity;

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
// Oracle DB의 ALERT 테이블과 연결
@Table(name = "ALERT")
public class Alert {

    // 기본키(PK) 지정 - 알림ID
    @Id
    @Column(name = "ALERT_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alertId;

    // 외래키(FK) - 반출ID, OUTBOUND 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OUTBOUND_ID", nullable = false)
    private Outbound outbound;

    // 알림내용 (NOT NULL, 최대 500자)
    @Column(name = "ALERT_CONTENT", length = 500, nullable = false)
    private String alertContent;

    // 처리상태 - 읽음/안읽음 (NULL 허용, 최대 20자)
    @Column(name = "PROCESS_STATUS", length = 20)
    private String processStatus;

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

    public String getAlertContent() {
        return alertContent;
    }

    public void setAlertContent(String alertContent) {
        this.alertContent = alertContent;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }
}