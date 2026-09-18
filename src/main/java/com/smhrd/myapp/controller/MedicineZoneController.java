package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Medicine;
import com.smhrd.myapp.service.MedicineZoneService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// users.html 하단 구역(Zone) 설정 화면용 - 관리자 전용 (페이지 자체가 admin-guard로 막혀있음)
@RestController
@RequestMapping("/api/medicine-zones")
public class MedicineZoneController {

    private final MedicineZoneService medicineZoneService;
    private final SubscriptionService subscriptionService;

    public MedicineZoneController(MedicineZoneService medicineZoneService, SubscriptionService subscriptionService) {
        this.medicineZoneService = medicineZoneService;
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

        List<Map<String, Object>> result = medicineZoneService.listByAdmin(adminId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            String medicineName = String.valueOf(body.get("medicineName"));
            Long cameraId = Long.valueOf(String.valueOf(body.get("cameraId")));

            String highRiskYn = body.get("highRiskYn") != null ? String.valueOf(body.get("highRiskYn")) : null;
            String manufacturer = body.get("manufacturer") != null ? String.valueOf(body.get("manufacturer")) : null;
            LocalDate registerDate = body.get("registerDate") != null
                    ? LocalDate.parse(String.valueOf(body.get("registerDate")))
                    : null;

            Medicine saved = medicineZoneService.create(
                    adminId, medicineName, highRiskYn, manufacturer, registerDate, cameraId
            );

            return ResponseEntity.ok(toMap(saved));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{medicineId}")
    public ResponseEntity<?> update(
            @PathVariable Long medicineId,
            @RequestBody Map<String, Object> body,
            HttpSession session
    ) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            Medicine updated = medicineZoneService.update(
                    adminId,
                    medicineId,
                    body.get("medicineName") != null ? String.valueOf(body.get("medicineName")) : null,
                    body.get("highRiskYn") != null ? String.valueOf(body.get("highRiskYn")) : null,
                    body.get("manufacturer") != null ? String.valueOf(body.get("manufacturer")) : null,
                    body.get("registerDate") != null ? LocalDate.parse(String.valueOf(body.get("registerDate"))) : null,
                    body.get("cameraId") != null ? Long.valueOf(String.valueOf(body.get("cameraId"))) : null,
                    body.get("regionX") != null ? Double.valueOf(String.valueOf(body.get("regionX"))) : null,
                    body.get("regionY") != null ? Double.valueOf(String.valueOf(body.get("regionY"))) : null,
                    body.get("regionWidth") != null ? Double.valueOf(String.valueOf(body.get("regionWidth"))) : null,
                    body.get("regionHeight") != null ? Double.valueOf(String.valueOf(body.get("regionHeight"))) : null
            );

            return ResponseEntity.ok(toMap(updated));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{medicineId}")
    public ResponseEntity<?> delete(@PathVariable Long medicineId, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            medicineZoneService.delete(adminId, medicineId);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Medicine medicine) {

        Map<String, Object> map = new java.util.HashMap<>();

        map.put("medicineId", medicine.getMedicineId());
        map.put("medicineName", medicine.getMedicineName());
        map.put("highRiskYn", medicine.getHighRiskYn());
        map.put("manufacturer", medicine.getManufacturer());
        map.put("registerDate", medicine.getRegisterDate());
        map.put("cameraId", medicine.getCamera() != null ? medicine.getCamera().getCameraId() : null);
        map.put("regionX", medicine.getRegionX());
        map.put("regionY", medicine.getRegionY());
        map.put("regionWidth", medicine.getRegionWidth());
        map.put("regionHeight", medicine.getRegionHeight());

        return map;
    }
}
