package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Ward;
import com.smhrd.myapp.service.SubscriptionService;
import com.smhrd.myapp.service.WardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// users.html 병동 관리 화면용 - 관리자 전용 (페이지 자체가 admin-guard로 막혀있음)
@RestController
@RequestMapping("/api/wards")
public class WardController {

    private final WardService wardService;
    private final SubscriptionService subscriptionService;

    public WardController(WardService wardService, SubscriptionService subscriptionService) {
        this.wardService = wardService;
        this.subscriptionService = subscriptionService;
    }

    private String requireAdmin(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null || !subscriptionService.isActiveAdmin(memberId)) {
            return null;
        }

        return memberId;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        List<Map<String, Object>> result = wardService.listByAdmin(adminId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, String> body, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            Ward saved = wardService.add(adminId, body.get("wardCode"), body.get("wardName"), body.get("location"));

            return ResponseEntity.ok(toMap(saved));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{wardSeqId}")
    public ResponseEntity<?> update(
            @PathVariable Long wardSeqId,
            @RequestBody Map<String, String> body,
            HttpSession session
    ) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            Ward updated = wardService.update(adminId, wardSeqId, body.get("wardName"), body.get("location"));

            return ResponseEntity.ok(toMap(updated));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{wardSeqId}")
    public ResponseEntity<?> delete(@PathVariable Long wardSeqId, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            wardService.delete(adminId, wardSeqId);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Ward ward) {

        Map<String, Object> map = new HashMap<>();

        map.put("wardSeqId", ward.getWardSeqId());
        map.put("wardCode", ward.getWardCode());
        map.put("wardName", ward.getWardName());
        map.put("location", ward.getLocation());

        return map;
    }
}
