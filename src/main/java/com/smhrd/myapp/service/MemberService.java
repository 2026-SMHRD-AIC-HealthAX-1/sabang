package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberService {

	
	private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    // 회원 전체 조회
    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    // 회원 한 명 조회
    public Member findById(Long memberId) {
        return memberRepository.findById(memberId).orElse(null);
    }

    // 회원 저장
    public Member save(Member member) {
        return memberRepository.save(member);
    }
}
