package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.service.MemberService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class MemberController {

	private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 회원 목록 확인
    @GetMapping("/member/list")
    public String memberList(Model model) {

        List<Member> memberList = memberService.findAll();

        model.addAttribute("memberList", memberList);

        return "member/list";
    }
}
