package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    // 관리자(=병원)의 알림 목록 - 의약품을 등록한 관리자 기준, 최신순
    List<Alert> findByMedicine_Admin_MemberIdOrderByAlertTimeDesc(String adminId);

    // 관리자가 아직 처리하지 않은 알림 수
    long countByMedicine_Admin_MemberIdAndProcessStatus(String adminId, String processStatus);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 의약품에 걸린 알림 전체 삭제
    void deleteByMedicine_Admin_MemberId(String adminId);
}
