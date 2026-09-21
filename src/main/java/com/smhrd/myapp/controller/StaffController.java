package com.smhrd.myapp.controller;

import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.MemberService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final HospitalStaffService hospitalStaffService;
    private final MemberService memberService;
    private final SubscriptionService subscriptionService;

    public StaffController(
            HospitalStaffService hospitalStaffService,
            MemberService memberService,
            SubscriptionService subscriptionService
    ) {
        this.hospitalStaffService = hospitalStaffService;
        this.memberService = memberService;
        this.subscriptionService = subscriptionService;
    }

    // 로그인 + 유효 구독 보유(관리자)인 경우에만 memberId 반환, 아니면 null
    private String requireAdmin(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null || !subscriptionService.isActiveAdmin(memberId)) {
            return null;
        }

        return memberId;
    }

    // 사용자관리 화면: 전체 회원(본인 제외) + 이미 권한을 부여했는지 여부
    @GetMapping("/candidates")
    public ResponseEntity<?> candidates(HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        Set<String> grantedIds = hospitalStaffService.listStaffOf(adminId).stream()
                .map(record -> record.getStaff().getMemberId())
                .collect(Collectors.toSet());

        // 후보 목록에는 (a) 이미 이 관리자 소속인 사람(직원 목록 표시용) 또는
        // (b) 아직 어디에도 소속 안 된 사람만 보여준다. 다른 병원 직원이거나 다른 관리자는 빼서
        // 한 사람이 두 병원에 동시 소속되는 걸 애초에 검색 결과에서부터 막는다.
        List<Map<String, Object>> result = memberService.findAll().stream()
                .filter(member -> !member.getMemberId().equals(adminId))
                .filter(member -> grantedIds.contains(member.getMemberId())
                        || (!hospitalStaffService.hasAccess(member.getMemberId())
                            && !subscriptionService.isActiveAdmin(member.getMemberId())))
                .map(member -> Map.<String, Object>of(
                        "memberId", member.getMemberId(),
                        "memberName", member.getMemberName(),
                        "email", member.getEmail(),
                        "granted", grantedIds.contains(member.getMemberId())
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // 하위 사용자에게 대시보드 접근 권한 부여
    @PostMapping
    public ResponseEntity<?> grant(@RequestBody Map<String, String> body, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        try {

            hospitalStaffService.grantAccess(adminId, body.get("staffId"));

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        }
    }

    // 권한 회수
    @DeleteMapping("/{staffId}")
    public ResponseEntity<?> revoke(@PathVariable String staffId, HttpSession session) {

        String adminId = requireAdmin(session);

        if (adminId == null) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 이용할 수 있습니다."));
        }

        hospitalStaffService.revokeAccess(adminId, staffId);

        return ResponseEntity.ok(Map.of("success", true));
    }
}
