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

    // 병동/의약품/시간대별 집계는 프론트에서 계산하므로 원본 출고 기록을 그대로 반환
    @GetMapping("/outbound-log")
    public ResponseEntity<?> outboundLog(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        boolean hasAccess = memberId != null
                && (subscriptionService.isActiveAdmin(memberId) || hospitalStaffService.hasAccess(memberId));

        if (!hasAccess) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        return ResponseEntity.ok(analyticsService.getOutboundLog());
    }
}
