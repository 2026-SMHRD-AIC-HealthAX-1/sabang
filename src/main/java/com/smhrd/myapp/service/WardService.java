package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.entity.Ward;
import com.smhrd.myapp.repository.OutboundRepository;
import com.smhrd.myapp.repository.SlipRepository;
import com.smhrd.myapp.repository.WardRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// users.html 병동 관리 화면용 CRUD - 병원(관리자)별로 병동을 구분한다.
// 병동번호(WARD_CODE)는 병원 안에서만 유일하면 되고, 실제 PK는 대체키(WARD_SEQ_ID)를 쓴다.
@Service
public class WardService {

    private final WardRepository wardRepository;
    private final SlipRepository slipRepository;
    private final OutboundRepository outboundRepository;
    private final MemberService memberService;

    public WardService(
            WardRepository wardRepository,
            SlipRepository slipRepository,
            OutboundRepository outboundRepository,
            MemberService memberService
    ) {
        this.wardRepository = wardRepository;
        this.slipRepository = slipRepository;
        this.outboundRepository = outboundRepository;
        this.memberService = memberService;
    }

    public List<Ward> listByAdmin(String adminId) {
        return wardRepository.findByAdmin_MemberId(adminId);
    }

    private Ward requireOwnedWard(String adminId, Long wardSeqId) {

        Ward ward = wardRepository.findById(wardSeqId).orElse(null);

        if (ward == null || !ward.getAdmin().getMemberId().equals(adminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 병동입니다.");
        }

        return ward;
    }

    public Ward add(String adminId, String wardCode, String wardName, String location) {

        String code = wardCode == null ? "" : wardCode.trim();
        String name = wardName == null ? "" : wardName.trim();
        String loc = location == null ? "" : location.trim();

        if (code.isEmpty() || code.length() > 100) {
            throw new IllegalStateException("병동번호를 1~100자로 입력해 주세요.");
        }

        if (name.isEmpty() || name.length() > 50) {
            throw new IllegalStateException("병동명을 1~50자로 입력해 주세요.");
        }

        if (wardRepository.countByAdmin_MemberIdAndWardCode(adminId, code) > 0) {
            throw new IllegalStateException("이미 등록된 병동번호입니다.");
        }

        Member admin = memberService.findById(adminId);

        if (admin == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        Ward ward = new Ward();
        ward.setWardCode(code);
        ward.setWardName(name);
        ward.setLocation(loc.isEmpty() ? null : loc);
        ward.setAdmin(admin);

        return wardRepository.save(ward);
    }

    // 병동명/위치 수정: 본인 병원의 병동만 수정할 수 있다 (병동번호는 지금은 그대로 두고, 이름/위치만)
    public Ward update(String adminId, Long wardSeqId, String wardName, String location) {

        Ward ward = requireOwnedWard(adminId, wardSeqId);

        String name = wardName == null ? "" : wardName.trim();
        String loc = location == null ? "" : location.trim();

        if (name.isEmpty() || name.length() > 50) {
            throw new IllegalStateException("병동명을 1~50자로 입력해 주세요.");
        }

        ward.setWardName(name);
        ward.setLocation(loc.isEmpty() ? null : loc);

        return wardRepository.save(ward);
    }

    // 병동 삭제: 본인 병원의 병동만, 전표/출고 기록에서 이미 쓰이고 있으면 FK 제약 위반을 막기 위해 삭제하지 못하게 한다
    public void delete(String adminId, Long wardSeqId) {

        Ward ward = requireOwnedWard(adminId, wardSeqId);

        if (slipRepository.countByWard_WardSeqId(wardSeqId) > 0 || outboundRepository.countByWard_WardSeqId(wardSeqId) > 0) {
            throw new IllegalStateException("이 병동을 사용 중인 전표·출고 기록이 있어 삭제할 수 없습니다.");
        }

        wardRepository.delete(ward);
    }
}
