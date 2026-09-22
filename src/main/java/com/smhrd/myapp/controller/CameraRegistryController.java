package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Camera;
import com.smhrd.myapp.service.CameraRegistryService;
import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 카메라 조회는 대시보드 접근 권한(관리자+직원) 모두 가능,
// 등록/삭제는 구독 중인 관리자만 가능
@RestController
@RequestMapping("/api/cameras")
public class CameraRegistryController {

    private final CameraRegistryService cameraRegistryService;
    private final SubscriptionService subscriptionService;
    private final HospitalStaffService hospitalStaffService;

    public CameraRegistryController(
            CameraRegistryService cameraRegistryService,
            SubscriptionService subscriptionService,
            HospitalStaffService hospitalStaffService
    ) {
        this.cameraRegistryService = cameraRegistryService;
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

    // 카메라는 관리자 본인 소유 기준으로 등록되므로, 직원은 자신이 속한 관리자의 카메라를 봐야 한다.
    // HospitalStaff에 자신을 등록한 관리자ID가 없으면(=admin 본인이면) 그대로 자신의 ID를 사용.
    // 소속 관리자의 구독이 만료되면 직원도 조회할 수 없다 (resolveActiveOwnerAdminId 참고).
    private String resolveOwnerAdminId(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return null;
        }

        return hospitalStaffService.resolveActiveOwnerAdminId(memberId);
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {

        String ownerAdminId = resolveOwnerAdminId(session);

        if (ownerAdminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        List<Map<String, Object>> result = cameraRegistryService.listByAdmin(ownerAdminId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // 파이썬(camera.py)이 자기 병원(관리자)의 카메라 목록을 가져오는 자리 - 브라우저 세션이 없는
    // 서버-서버 호출이라 인증 없이 연다 (medicine-zones/camera/{cameraId}와 같은 패턴).
    // camera.py는 이 목록의 streamUrl 끝자리(파이썬 내부 카메라 인덱스)로 실제 CAMERA_ID를 찾고,
    // cameraRole로 OCR_SCAN 카메라가 어느 것인지 알아낸다.
    @GetMapping("/by-admin/{adminId}")
    public ResponseEntity<?> listByAdminForService(@PathVariable String adminId) {

        List<Map<String, Object>> result = cameraRegistryService.listByAdmin(adminId).stream()
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

        String cameraName = body.get("cameraName");

        if (cameraName == null || cameraName.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("message", "카메라 이름을 입력해 주세요."));
        }

        try {

            // 안 넘어오면 CameraRegistryService.add()가 기본값(MONITOR)로 처리함
            Camera saved = cameraRegistryService.add(adminId, cameraName, body.get("cameraRole"));

            return ResponseEntity.ok(toMap(saved));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
        }
    }

    // 카메라 용도 변경 (모니터링용 <-> OCR 스캔용)
    @PutMapping("/{cameraId}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable Long cameraId,
            @RequestBody Map<String, String> body,
            HttpSession session
    ) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            Camera updated = cameraRegistryService.setRole(adminId, cameraId, body.get("cameraRole"));

            return ResponseEntity.ok(toMap(updated));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{cameraId}")
    public ResponseEntity<?> delete(@PathVariable Long cameraId, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            cameraRegistryService.delete(adminId, cameraId);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Camera camera) {

        return Map.of(
                "cameraId", camera.getCameraId(),
                "cameraName", camera.getCameraName(),
                "connectionStatus", camera.getConnectionStatus(),
                "streamUrl", camera.getStreamUrl(),
                "cameraRole", camera.getCameraRole()
        );
    }
}
