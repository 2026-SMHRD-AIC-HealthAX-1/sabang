package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.SlipItem;
import com.smhrd.myapp.entity.SlipItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlipItemRepository extends JpaRepository<SlipItem, SlipItemId> {

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 의약품이 적힌 전표 품목 전체 삭제
    // OUTBOUND가 (SLIP_ID, MEDICINE_ID) 복합키로 이 테이블을 참조하므로 OUTBOUND 삭제 후에 호출해야 함
    void deleteByMedicine_Admin_MemberId(String adminId);

    // 의약품 삭제 전 확인용: 이 의약품이 적힌 전표 품목이 있는지
    long countByMedicine_MedicineId(Long medicineId);
}
