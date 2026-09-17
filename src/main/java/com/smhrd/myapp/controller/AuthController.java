package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.MemberService;
import com.smhrd.myapp.service.SubscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final MemberService memberService;
    private final SubscriptionService subscriptionService;
    private final HospitalStaffService hospitalStaffService;

    public AuthController(
            MemberService memberService,
            SubscriptionService subscriptionService,
            HospitalStaffService hospitalStaffService
    ) {
        this.memberService = memberService;
        this.subscriptionService = subscriptionService;
        this.hospitalStaffService = hospitalStaffService;
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

    // 회원탈퇴: 구독/권한 관련 데이터를 먼저 정리한 뒤 회원 삭제 (FK 제약 위반 방지)
    @DeleteMapping("/me")
    public ResponseEntity<?> withdraw(HttpSession session) {

        String memberId = (String) session.getAttribute(SESSION_MEMBER_ID);

        if (memberId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        subscriptionService.deleteAllByAdmin(memberId);
        hospitalStaffService.deleteAllGrantedBy(memberId);
        hospitalStaffService.deleteAllAccessOf(memberId);
        memberService.delete(memberId);

        session.invalidate();

        return ResponseEntity.ok(Map.of("success", true));
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
