package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.HospitalStaff;
import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.HospitalStaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HospitalStaffService {

    private final HospitalStaffRepository hospitalStaffRepository;
    private final MemberService memberService;

    public HospitalStaffService(HospitalStaffRepository hospitalStaffRepository, MemberService memberService) {
        this.hospitalStaffRepository = hospitalStaffRepository;
        this.memberService = memberService;
    }

    // 관리자에게 대시보드 접근 권한을 부여받은 직원인지 확인
    public boolean hasAccess(String memberId) {
        return hospitalStaffRepository.countByStaff_MemberId(memberId) > 0;
    }

    public boolean isGrantedBy(String adminId, String staffId) {
        return hospitalStaffRepository.countByAdmin_MemberIdAndStaff_MemberId(adminId, staffId) > 0;
    }

    // 관리자가 하위 사용자에게 대시보드 접근 권한 부여
    public void grantAccess(String adminId, String staffId) {

        if (adminId.equals(staffId)) {
            throw new IllegalStateException("본인에게는 권한을 부여할 수 없습니다.");
        }

        if (isGrantedBy(adminId, staffId)) {
            throw new IllegalStateException("이미 권한이 부여된 사용자입니다.");
        }

        Member admin = memberService.findById(adminId);
        Member staff = memberService.findById(staffId);

        if (admin == null || staff == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        HospitalStaff record = new HospitalStaff();
        // HISTORY_ID가 시퀀스/트리거로 자동 채워지지 않아 직접 계산해서 넣어줌
        record.setHistoryId(hospitalStaffRepository.findMaxId() + 1);
        record.setAdmin(admin);
        record.setStaff(staff);

        hospitalStaffRepository.save(record);
    }

    // 이 관리자가 권한을 부여한 직원 목록
    public List<HospitalStaff> listStaffOf(String adminId) {
        return hospitalStaffRepository.findByAdmin_MemberId(adminId);
    }

    // 이 직원에게 권한을 부여한 관리자ID (카메라 등 관리자 소유 자원을 직원이 같이 볼 때 사용)
    public String findAdminIdOf(String staffId) {

        List<HospitalStaff> records = hospitalStaffRepository.findByStaff_MemberId(staffId);

        return records.isEmpty() ? null : records.get(0).getAdmin().getMemberId();
    }

    // 권한 회수
    // deleteBy 파생 쿼리는 대상을 조회한 뒤 entityManager.remove()로 지우기 때문에
    // 트랜잭션이 없으면 실패함 (No EntityManager with actual transaction available)
    @Transactional
    public void revokeAccess(String adminId, String staffId) {
        hospitalStaffRepository.deleteByAdmin_MemberIdAndStaff_MemberId(adminId, staffId);
    }

    // 회원탈퇴 시 정리: 이 관리자가 부여한 권한 전체 삭제 (하위 직원들은 접근 권한을 잃음)
    @Transactional
    public void deleteAllGrantedBy(String adminId) {
        hospitalStaffRepository.deleteByAdmin_MemberId(adminId);
    }

    // 회원탈퇴 시 정리: 이 회원이 다른 관리자에게 부여받았던 권한 삭제
    @Transactional
    public void deleteAllAccessOf(String staffId) {
        hospitalStaffRepository.deleteByStaff_MemberId(staffId);
    }
}
