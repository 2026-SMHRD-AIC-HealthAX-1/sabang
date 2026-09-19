package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Outbound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboundRepository extends JpaRepository<Outbound, Long> {

    // 출고한 의약품을 등록한 관리자(=병원) 기준으로 조회
    List<Outbound> findByMedicine_Admin_MemberId(String adminId);
}
