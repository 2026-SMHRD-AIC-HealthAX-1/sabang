package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Slip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlipRepository extends JpaRepository<Slip, String> {

    // 병동 삭제 전 확인용: 이 병동을 참조하는 전표가 있는지
    long countByWard_WardSeqId(Long wardSeqId);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 병동에 딸린 전표 헤더 전체 삭제
    // (SLIP_ITEM/OUTBOUND는 의약품 기준으로 먼저 삭제된 뒤 호출해야 함)
    void deleteByWard_Admin_MemberId(String adminId);
}
