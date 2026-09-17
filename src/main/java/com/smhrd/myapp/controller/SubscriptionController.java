package com.smhrd.myapp.controller;

import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // 결제(데모): 새 구독 레코드 생성 후 관리자 자격 부여
    @PostMapping("/pay")
    public ResponseEntity<?> pay(@RequestBody Map<String, Object> body, HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        try {

            int months = Integer.parseInt(String.valueOf(body.get("months")));
            String paymentMethod = String.valueOf(body.get("paymentMethod"));

            subscriptionService.pay(memberId, months, paymentMethod);

            return ResponseEntity.ok(Map.of("success", true));

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
