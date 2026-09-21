package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Outbound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboundRepository extends JpaRepository<Outbound, Long> {

    // 출고한 의약품을 등록한 관리자(=병원) 기준으로 조회
    List<Outbound> findByMedicine_Admin_MemberId(String adminId);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 의약품에 대한 출고 기록 전체 삭제
    void deleteByMedicine_Admin_MemberId(String adminId);

    // 병동 삭제 전 확인용: 이 병동을 참조하는 출고 기록이 있는지
    long countByWard_WardSeqId(Long wardSeqId);

    // 의약품 삭제 전 확인용: 이 의약품에 대한 출고 기록이 있는지
    long countByMedicine_MedicineId(Long medicineId);
}
