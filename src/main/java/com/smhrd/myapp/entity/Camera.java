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
// Oracle DB의 CAMERA 테이블과 연결
@Table(name = "CAMERA")
public class Camera {

    // 기본키(PK) 지정 - 카메라ID
    // IDENTITY 자동증가는 캠퍼스 오라클 DB 버전에서 지원 안 될 수 있어 제외 (필요 시 회의 후 재도입)
    @Id
    @Column(name = "CAMERA_ID")
    private Long cameraId;

    // 외래키(FK) - 관리자ID, MEMBER 테이블 참조 (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ADMIN_ID", nullable = false)
    private Member admin;

    // 카메라명 (NOT NULL, 최대 100자)
    @Column(name = "CAMERA_NAME", length = 100, nullable = false)
    private String cameraName;

    // 연결상태 - 연결/연결끊김 등의 상태 (NULL 허용, 최대 20자)
    @Column(name = "CONNECTION_STATUS", length = 20)
    private String connectionStatus;

    // 기본 생성자 (JPA는 파라미터 없는 생성자가 필수예요)
    public Camera() {
    }

    // Getter / Setter

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public Member getAdmin() {
        return admin;
    }

    public void setAdmin(Member admin) {
        this.admin = admin;
    }

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }
}