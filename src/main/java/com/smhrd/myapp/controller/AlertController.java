package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Alert;
import com.smhrd.myapp.service.AlertService;
import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 목록 조회는 관리자+직원 모두 가능하지만, 직원에게는 재고부족(LOW_STOCK) 알림만 보여준다
// (이상/불일치 알림은 관리자만 볼 수 있음). 확인 처리는 관리자만 가능하다.
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final SubscriptionService subscriptionService;
    private final HospitalStaffService hospitalStaffService;

    public AlertController(
            AlertService alertService,
            SubscriptionService subscriptionService,
            HospitalStaffService hospitalStaffService
    ) {
        this.alertService = alertService;
        this.subscriptionService = subscriptionService;
        this.hospitalStaffService = hospitalStaffService;
    }

    private String requireAdmin(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null || !subscriptionService.isActiveAdmin(memberId)) {
            return null;
        }

        return memberId;
    }

    // 카메라/전표 조회와 같은 패턴: 관리자 본인이거나, 그 관리자에게 속한(구독 유효한) 직원이면 통과
    private String resolveOwnerAdminId(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return null;
        }

        return hospitalStaffService.resolveActiveOwnerAdminId(memberId);
    }

    // 알림 목록 (최신순). 관리자는 전체, 직원은 재고부족만 받는다.
    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");
        String ownerAdminId = resolveOwnerAdminId(session);

        if (ownerAdminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        boolean isAdmin = subscriptionService.isActiveAdmin(memberId);

        List<Alert> alerts = isAdmin
                ? alertService.listByAdmin(ownerAdminId)
                : alertService.listByAdminAndType(ownerAdminId, Alert.TYPE_LOW_STOCK);

        List<Map<String, Object>> result = alerts.stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // 직원 대시보드용 - 어떤 의약품인지/무슨 내용인지는 안 주고, 미처리 알림들이
    // 언제·무슨 종류로 있었는지만 목록으로 알려준다 (회의 결과: 직원은 상세 내용 대신
    // "OO시 OO분경에 OO 알림이 감지되었습니다" 문구만 여러 줄로 봄, 최신 1건만이 아님).
    // 관리자가 호출해도 동작은 하지만, 관리자는 /api/alerts(전체 목록)를 쓰므로 실제로는 안 씀.
    @GetMapping("/notices")
    public ResponseEntity<?> notices(HttpSession session) {

        String ownerAdminId = resolveOwnerAdminId(session);

        if (ownerAdminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        List<Map<String, Object>> result = alertService.listPending(ownerAdminId).stream()
                .map(alert -> {

                    Map<String, Object> row = new HashMap<>();
                    row.put("alertType", alert.getAlertType());
                    row.put("alertTime", alert.getAlertTime());

                    return row;
                })
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
