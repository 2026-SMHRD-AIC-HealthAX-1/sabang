package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.MemberRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {


	private final MemberRepository memberRepository;

    // memberId가 Long -> String으로 바뀌면서 findById 인자 타입도 맞춰야 컴파일됨
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 회원 전체 조회
    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    // 회원 한 명 조회
    public Member findById(String memberId) {
        return memberRepository.findById(memberId).orElse(null);
    }

    // 회원 저장
    public Member save(Member member) {
        return memberRepository.save(member);
    }

    // 회원탈퇴: 구독/권한 데이터는 호출하는 쪽(AuthController)에서 먼저 정리한 뒤 호출해야 함
    public void delete(String memberId) {
        memberRepository.deleteById(memberId);
    }

    // 아이디/이메일/전화번호 중복 확인
    public boolean isDuplicate(String field, String value) {

        return switch (field) {
            case "id" -> memberRepository.existsById(value);
            case "email" -> memberRepository.countByEmail(value) > 0;
            case "phone" -> memberRepository.countByPhone(value) > 0;
            default -> throw new IllegalArgumentException("알 수 없는 중복 확인 항목입니다: " + field);
        };
    }

    // 회원가입: 중복 확인 후 비밀번호를 암호화해서 저장
    public Member register(Member member) {

        if (memberRepository.existsById(member.getMemberId())) {
            throw new IllegalStateException("이미 사용 중인 아이디입니다.");
        }

        if (memberRepository.countByEmail(member.getEmail()) > 0) {
            throw new IllegalStateException("이미 등록된 이메일입니다.");
        }

        if (memberRepository.countByPhone(member.getPhone()) > 0) {
            throw new IllegalStateException("이미 등록된 전화번호입니다.");
        }

        member.setPassword(passwordEncoder.encode(member.getPassword()));

        return memberRepository.save(member);
    }

    // 로그인: 아이디로 조회 후 암호화된 비밀번호 비교
    public Member authenticate(String memberId, String rawPassword) {

        Member member = memberRepository.findById(memberId).orElse(null);

        if (member == null || !passwordEncoder.matches(rawPassword, member.getPassword())) {
            return null;
        }

        return member;
    }

    // 아이디 찾기: 이메일 + 전화번호가 일치하는 회원의 아이디 반환
    public String findMemberId(String email, String phone) {

        List<Member> found = memberRepository.findByEmailAndPhone(email, phone);

        return found.isEmpty() ? null : found.get(0).getMemberId();
    }

    // 비밀번호 재설정 전 본인 확인: 아이디 + 이메일 + 전화번호 일치 여부
    public boolean verifyForPasswordReset(String memberId, String email, String phone) {

        List<Member> found = memberRepository.findByMemberIdAndEmailAndPhone(memberId, email, phone);

        return !found.isEmpty();
    }

    // 비밀번호 재설정
    public void resetPassword(String memberId, String newPassword) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 회원입니다."));

        member.setPassword(passwordEncoder.encode(newPassword));

        memberRepository.save(member);
    }

    // 개인정보 설정: 이름 변경
    public Member updateName(String memberId, String name) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 회원입니다."));

        member.setMemberName(name);

        return memberRepository.save(member);
    }
}
