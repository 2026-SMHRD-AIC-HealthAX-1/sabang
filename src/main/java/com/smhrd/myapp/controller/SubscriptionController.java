package com.smhrd.myapp.controller;

import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final HospitalStaffService hospitalStaffService;

    public SubscriptionController(SubscriptionService subscriptionService, HospitalStaffService hospitalStaffService) {
        this.subscriptionService = subscriptionService;
        this.hospitalStaffService = hospitalStaffService;
    }

    // 결제(데모): 새 구독 레코드 생성 후 관리자 자격 부여
    // 이미 다른 병원 직원인 사람이 결제해서 동시에 자기 병원 관리자도 되면
    // HospitalStaffService.resolveActiveOwnerAdminId()가 본인을 관리자로 먼저 판정해버려서
    // 원래 소속 병원 데이터를 못 보게 된다. grantAccess() 쪽에서 반대 방향은 이미 막아놔서
    // 여기도 대칭으로 막는다.
    @PostMapping("/pay")
    public ResponseEntity<?> pay(@RequestBody Map<String, Object> body, HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        if (hospitalStaffService.hasAccess(memberId)) {
            return ResponseEntity.status(400).body(Map.of("message", "이미 다른 병원 소속 직원이라 구독할 수 없습니다."));
        }

        if (body.get("months") == null || body.get("paymentMethod") == null) {
            return ResponseEntity.status(400).body(Map.of("message", "구독 기간과 결제수단을 입력해 주세요."));
        }

        try {

            int months = Integer.parseInt(String.valueOf(body.get("months")));
            String paymentMethod = String.valueOf(body.get("paymentMethod"));

            subscriptionService.pay(memberId, months, paymentMethod);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(400).body(Map.of("message", "구독 기간을 올바르게 입력해 주세요."));

        } catch (IllegalArgumentException | IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    // profile.html 구독결제 정보 (관리자 전용)
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        Map<String, Object> billing = subscriptionService.getBillingInfo(memberId);

        if (billing == null) {
            return ResponseEntity.status(404).body(Map.of("message", "구독 내역이 없습니다."));
        }

        return ResponseEntity.ok(billing);
    }
}
