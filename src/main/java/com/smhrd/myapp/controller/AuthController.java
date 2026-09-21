package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.HospitalStaff;
import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.AlertRepository;
import com.smhrd.myapp.repository.CameraRepository;
import com.smhrd.myapp.repository.MedicineRepository;
import com.smhrd.myapp.repository.OutboundRepository;
import com.smhrd.myapp.repository.SlipItemRepository;
import com.smhrd.myapp.repository.SlipRepository;
import com.smhrd.myapp.repository.WardRepository;
import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.MemberService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final MemberService memberService;
    private final SubscriptionService subscriptionService;
    private final HospitalStaffService hospitalStaffService;
    private final AlertRepository alertRepository;
    private final OutboundRepository outboundRepository;
    private final SlipItemRepository slipItemRepository;
    private final SlipRepository slipRepository;
    private final MedicineRepository medicineRepository;
    private final CameraRepository cameraRepository;
    private final WardRepository wardRepository;

    public AuthController(
            MemberService memberService,
            SubscriptionService subscriptionService,
            HospitalStaffService hospitalStaffService,
            AlertRepository alertRepository,
            OutboundRepository outboundRepository,
            SlipItemRepository slipItemRepository,
            SlipRepository slipRepository,
            MedicineRepository medicineRepository,
            CameraRepository cameraRepository,
            WardRepository wardRepository
    ) {
        this.memberService = memberService;
        this.subscriptionService = subscriptionService;
        this.hospitalStaffService = hospitalStaffService;
        this.alertRepository = alertRepository;
        this.outboundRepository = outboundRepository;
        this.slipItemRepository = slipItemRepository;
        this.slipRepository = slipRepository;
        this.medicineRepository = medicineRepository;
        this.cameraRepository = cameraRepository;
        this.wardRepository = wardRepository;
    }

    // 관리자(유효 구독 보유) 또는 권한을 부여받은 직원이면 대시보드 접근 가능
    private boolean hasDashboardAccess(String memberId) {
        return subscriptionService.isActiveAdmin(memberId) || hospitalStaffService.hasAccess(memberId);
    }

    // 아이디/이메일/전화번호 중복 확인
    @GetMapping("/check")
    public ResponseEntity<?> checkDuplicate(@RequestParam String field, @RequestParam String value) {

        boolean duplicate = memberService.isDuplicate(field, value);

        return ResponseEntity.ok(Map.of("duplicate", duplicate));
    }

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Member member) {

        try {

            Member saved = memberService.register(member);

            return ResponseEntity.ok(Map.of(
                    "memberId", saved.getMemberId(),
                    "memberName", saved.getMemberName()
            ));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
        }
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {

        String memberId = body.get("memberId");
        String password = body.get("password");

        Member member = memberService.authenticate(memberId, password);

        if (member == null) {
            return ResponseEntity.status(401).body(Map.of("message", "아이디 또는 비밀번호가 일치하지 않습니다."));
        }

        session.setAttribute(SESSION_MEMBER_ID, member.getMemberId());

        return ResponseEntity.ok(Map.of(
                "memberId", member.getMemberId(),
                "memberName", member.getMemberName(),
                "isAdmin", subscriptionService.isActiveAdmin(member.getMemberId()),
                "hasAccess", hasDashboardAccess(member.getMemberId())
        ));
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {

        session.invalidate();

        return ResponseEntity.ok(Map.of("success", true));
    }

    // 개인정보 설정: 이름 변경
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> body, HttpSession session) {

        String memberId = (String) session.getAttribute(SESSION_MEMBER_ID);

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        String name = body.get("memberName") == null ? "" : body.get("memberName").trim();

        if (name.isEmpty() || name.length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("message", "이름을 1~50자로 입력해 주세요."));
        }

        Member updated = memberService.updateName(memberId, name);

        return ResponseEntity.ok(Map.of(
                "memberId", updated.getMemberId(),
                "memberName", updated.getMemberName(),
                "email", updated.getEmail()
        ));
    }

    // 회원탈퇴: 관리자(유효 구독 보유)면 하위 직원 계정과 이 병원의 카메라/의약품/알림/출고/전표/병동
    // 기록까지 전부 정리한 뒤 삭제한다. 일반 직원은 본인이 부여받은/부여한 권한과 구독만 정리하고 삭제한다.
    // 자식 데이터를 FK 순서(알림 -> 출고 -> 전표품목 -> 전표 -> 의약품 -> 카메라 -> 병동)대로 먼저 지워야
    // MEMBER 삭제 시 제약 위반이 나지 않는다.
    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<?> withdraw(HttpSession session) {

        String memberId = (String) session.getAttribute(SESSION_MEMBER_ID);

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        boolean isAdmin = subscriptionService.isActiveAdmin(memberId);

        if (isAdmin) {

            List<HospitalStaff> staffRecords = hospitalStaffService.listStaffOf(memberId);

            for (HospitalStaff record : staffRecords) {

                String staffId = record.getStaff().getMemberId();

                subscriptionService.deleteAllByAdmin(staffId);
                hospitalStaffService.deleteAllGrantedBy(staffId);
                hospitalStaffService.deleteAllAccessOf(staffId);
                memberService.delete(staffId);
            }

            alertRepository.deleteByMedicine_Admin_MemberId(memberId);
            outboundRepository.deleteByMedicine_Admin_MemberId(memberId);
            slipItemRepository.deleteByMedicine_Admin_MemberId(memberId);
            slipRepository.deleteByWard_Admin_MemberId(memberId);
            medicineRepository.deleteByAdmin_MemberId(memberId);
            cameraRepository.deleteByAdmin_MemberId(memberId);
            wardRepository.deleteByAdmin_MemberId(memberId);
        }

        subscriptionService.deleteAllByAdmin(memberId);
        hospitalStaffService.deleteAllGrantedBy(memberId);
        hospitalStaffService.deleteAllAccessOf(memberId);
        memberService.delete(memberId);

        session.invalidate();

        return ResponseEntity.ok(Map.of("success", true, "cascaded", isAdmin));
    }

    // 현재 로그인 상태 확인
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {

        String memberId = (String) session.getAttribute(SESSION_MEMBER_ID);

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        Member member = memberService.findById(memberId);

        if (member == null) {
            session.invalidate();
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        return ResponseEntity.ok(Map.of(
                "memberId", member.getMemberId(),
                "memberName", member.getMemberName(),
                "email", member.getEmail(),
                "isAdmin", subscriptionService.isActiveAdmin(member.getMemberId()),
                "hasAccess", hasDashboardAccess(member.getMemberId())
        ));
    }

    // 아이디 찾기
    @PostMapping("/find-id")
    public ResponseEntity<?> findId(@RequestBody Map<String, String> body) {

        String memberId = memberService.findMemberId(body.get("email"), body.get("phone"));

        if (memberId == null) {
            return ResponseEntity.status(404).body(Map.of("message", "일치하는 회원 정보가 없습니다."));
        }

        return ResponseEntity.ok(Map.of("memberId", memberId));
    }

    // 비밀번호 찾기: 재설정 전 본인 확인
    @PostMapping("/find-password/verify")
    public ResponseEntity<?> verifyForPasswordReset(@RequestBody Map<String, String> body) {

        boolean verified = memberService.verifyForPasswordReset(
                body.get("memberId"),
                body.get("email"),
                body.get("phone")
        );

        if (!verified) {
            return ResponseEntity.status(404).body(Map.of("message", "일치하는 회원 정보가 없습니다."));
        }

        return ResponseEntity.ok(Map.of("verified", true));
    }

    // 비밀번호 재설정: memberId만으로 바꾸지 못하도록 이메일/전화번호까지 다시 확인
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {

        String memberId = body.get("memberId");

        boolean verified = memberService.verifyForPasswordReset(
                memberId,
                body.get("email"),
                body.get("phone")
        );

        if (!verified) {
            return ResponseEntity.status(404).body(Map.of("message", "일치하는 회원 정보가 없습니다."));
        }

        memberService.resetPassword(memberId, body.get("newPassword"));

        return ResponseEntity.ok(Map.of("success", true));
    }
}
