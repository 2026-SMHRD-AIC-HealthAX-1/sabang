package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.service.MemberService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_MEMBER_ID = "memberId";

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
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
                "memberName", member.getMemberName()
        ));
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {

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
                "memberName", member.getMemberName()
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
