package com.smhrd.myapp.controller;

import com.smhrd.myapp.service.AnalyticsService;
import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SubscriptionService subscriptionService;
    private final HospitalStaffService hospitalStaffService;

    public AnalyticsController(
            AnalyticsService analyticsService,
            SubscriptionService subscriptionService,
            HospitalStaffService hospitalStaffService
    ) {
        this.analyticsService = analyticsService;
        this.subscriptionService = subscriptionService;
        this.hospitalStaffService = hospitalStaffService;
    }

    // 관리자는 본인 병원, 직원은 자신이 속한 관리자의 병원 데이터만 볼 수 있다.
    // 소속 관리자의 구독이 만료되면 직원도 조회할 수 없다 (resolveActiveOwnerAdminId 참고).
    private String resolveOwnerAdminId(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return null;
        }

        return hospitalStaffService.resolveActiveOwnerAdminId(memberId);
    }

    // 병동/의약품/시간대별 집계는 프론트에서 계산하므로 원본 출고 기록을 그대로 반환
    @GetMapping("/outbound-log")
    public ResponseEntity<?> outboundLog(HttpSession session) {

        String ownerAdminId = resolveOwnerAdminId(session);

        if (ownerAdminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // 이상 알림 정보는 관리자만 볼 수 있으므로, 직원(관리자가 권한을 준 사용자)에게는 abnormal 값을 빼고 준다
        String memberId = (String) session.getAttribute("memberId");
        boolean isAdmin = subscriptionService.isActiveAdmin(memberId);

        return ResponseEntity.ok(analyticsService.getOutboundLog(ownerAdminId, isAdmin));
    }
}
