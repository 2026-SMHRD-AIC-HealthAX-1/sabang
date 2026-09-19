package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    // 관리자(=병원)의 알림 목록 - 의약품을 등록한 관리자 기준, 최신순
    List<Alert> findByMedicine_Admin_MemberIdOrderByAlertTimeDesc(String adminId);

    // 관리자가 아직 처리하지 않은 알림 수
    long countByMedicine_Admin_MemberIdAndProcessStatus(String adminId, String processStatus);
}
