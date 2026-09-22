package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    // 관리자(=병원)의 알림 목록 - 의약품을 등록한 관리자 기준, 최신순
    List<Alert> findByMedicine_Admin_MemberIdOrderByAlertTimeDesc(String adminId);

    // 직원에게는 재고부족(LOW_STOCK)만 보여주므로, 알림 종류로 걸러서 조회
    List<Alert> findByMedicine_Admin_MemberIdAndAlertTypeOrderByAlertTimeDesc(String adminId, String alertType);

    // 직원용 "알림(주의) 한 줄" 안내에 쓸, 종류 상관없이 가장 최근 미처리 알림
    List<Alert> findByMedicine_Admin_MemberIdAndProcessStatusOrderByAlertTimeDesc(String adminId, String processStatus);

    // 관리자가 아직 처리하지 않은 알림 수
    long countByMedicine_Admin_MemberIdAndProcessStatus(String adminId, String processStatus);

    // 의약품 삭제 전 확인용: 이 의약품에 걸린 알림이 있는지
    long countByMedicine_MedicineId(Long medicineId);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 의약품에 걸린 알림 전체 삭제
    void deleteByMedicine_Admin_MemberId(String adminId);
}
