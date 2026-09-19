package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Alert;
import com.smhrd.myapp.service.AlertService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 이상 알림은 구독 중인 관리자만 조회/처리할 수 있다
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final SubscriptionService subscriptionService;

    public AlertController(AlertService alertService, SubscriptionService subscriptionService) {
        this.alertService = alertService;
        this.subscriptionService = subscriptionService;
    }

    private String requireAdmin(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null || !subscriptionService.isActiveAdmin(memberId)) {
            return null;
        }

        return memberId;
    }

    // 알림 목록 (최신순)
    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        List<Map<String, Object>> result = alertService.listByAdmin(adminId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // 헤더 종 아이콘에 표시할 미처리 알림 수
    @GetMapping("/pending-count")
    public ResponseEntity<?> pendingCount(HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        return ResponseEntity.ok(Map.of("count", alertService.countPending(adminId)));
    }

    // 관리자 확인 처리
    @PutMapping("/{alertId}/process")
    public ResponseEntity<?> process(@PathVariable Long alertId, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            return ResponseEntity.ok(toMap(alertService.process(adminId, alertId)));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    // 관리자 일괄 확인 처리 (체크박스로 고른 여러 건)
    @PutMapping("/process-batch")
    public ResponseEntity<?> processBatch(@RequestBody Map<String, List<Long>> body, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        List<Long> alertIds = body.get("alertIds");

        if (alertIds == null || alertIds.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("message", "처리할 알림을 선택해 주세요."));
        }

        try {

            return ResponseEntity.ok(Map.of("processedCount", alertService.processAll(adminId, alertIds)));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Alert alert) {

        Map<String, Object> row = new HashMap<>();

        row.put("alertId", alert.getAlertId());
        row.put("alertType", alert.getAlertType());
        row.put("medicineName", alert.getMedicine().getMedicineName());
        row.put("alertContent", alert.getAlertContent());
        row.put("alertTime", alert.getAlertTime());
        row.put("processStatus", alert.getProcessStatus());

        return row;
    }
}
