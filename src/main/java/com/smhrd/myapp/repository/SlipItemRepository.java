package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.SlipItem;
import com.smhrd.myapp.entity.SlipItemId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlipItemRepository extends JpaRepository<SlipItem, SlipItemId> {

    // 전표 상세 화면용: 이 전표에 적힌 품목(의약품+요청수량) 전체, 줄 번호 순
    List<SlipItem> findById_SlipIdOrderById_ItemNoAsc(String slipId);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 의약품이 적힌 전표 품목 전체 삭제
    // OUTBOUND가 (SLIP_ID, MEDICINE_ID) 복합키로 이 테이블을 참조하므로 OUTBOUND 삭제 후에 호출해야 함
    void deleteByMedicine_Admin_MemberId(String adminId);

    // 의약품 삭제 전 확인용: 이 의약품이 적힌 전표 품목이 있는지
    long countByMedicine_MedicineId(Long medicineId);

    // 반출 결과 비교용: 이 전표에서 이 의약품의 요청수량이 얼마였는지
    // (SLIP_ID, MEDICINE_ID)는 UNIQUE라 있으면 한 건뿐)
    java.util.Optional<SlipItem> findById_SlipIdAndMedicine_MedicineId(String slipId, Long medicineId);
}
