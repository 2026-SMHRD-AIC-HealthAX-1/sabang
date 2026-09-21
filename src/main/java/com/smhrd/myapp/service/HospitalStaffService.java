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
    private final SubscriptionService subscriptionService;

    public HospitalStaffService(
            HospitalStaffRepository hospitalStaffRepository,
            MemberService memberService,
            SubscriptionService subscriptionService
    ) {
        this.hospitalStaffRepository = hospitalStaffRepository;
        this.memberService = memberService;
        this.subscriptionService = subscriptionService;
    }

    // 관리자에게 대시보드 접근 권한을 부여받은 직원인지 확인 (소속 관계 자체만 봄, 구독 유효성은 안 봄)
    public boolean hasAccess(String memberId) {
        return hospitalStaffRepository.countByStaff_MemberId(memberId) > 0;
    }

    // 대시보드 접근 가능한 관리자ID 해석 (컨트롤러 3곳에서 공통으로 쓰던 로직을 여기로 모음):
    // 본인이 유효 구독 관리자면 본인 ID, 직원이면 소속 관리자가 "지금도" 유효 구독 중일 때만 그 관리자 ID.
    // 소속 관리자의 구독이 만료되면 직원도 접근 불가(null) - hasAccess()만으로는 구독 만료를 못 잡아서 따로 뺐다.
    public String resolveActiveOwnerAdminId(String memberId) {

        if (subscriptionService.isActiveAdmin(memberId)) {
            return memberId;
        }

        if (hasAccess(memberId)) {

            String ownerAdminId = findAdminIdOf(memberId);

            if (ownerAdminId != null && subscriptionService.isActiveAdmin(ownerAdminId)) {
                return ownerAdminId;
            }
        }

        return null;
    }

    public boolean isGrantedBy(String adminId, String staffId) {
        return hospitalStaffRepository.countByAdmin_MemberIdAndStaff_MemberId(adminId, staffId) > 0;
    }

    // 관리자가 하위 사용자에게 대시보드 접근 권한 부여
    // 한 사용자가 동시에 두 병원 소속이 되면 findAdminIdOf()가 첫 번째 소속만 반환해서
    // 카메라/통계 등 병원 소유 자원 조회가 조용히 엉뚱한(혹은 아예 접근 불가한) 결과를 내게 된다.
    // 그래서 이미 다른 병원 직원이거나, 본인이 관리자(구독 중)인 사용자는 직원으로 추가할 수 없게 막는다.
    public void grantAccess(String adminId, String staffId) {

        if (adminId.equals(staffId)) {
            throw new IllegalStateException("본인에게는 권한을 부여할 수 없습니다.");
        }

        if (isGrantedBy(adminId, staffId)) {
            throw new IllegalStateException("이미 권한이 부여된 사용자입니다.");
        }

        if (hasAccess(staffId)) {
            throw new IllegalStateException("이미 다른 병원에 소속된 사용자입니다.");
        }

        if (subscriptionService.isActiveAdmin(staffId)) {
            throw new IllegalStateException("이미 관리자(병원)로 가입된 사용자입니다.");
        }

        Member admin = memberService.findById(adminId);
        Member staff = memberService.findById(staffId);

        if (admin == null || staff == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        HospitalStaff record = new HospitalStaff();
        record.setAdmin(admin);
        record.setStaff(staff);

        hospitalStaffRepository.save(record);
    }

    // 이 관리자가 권한을 부여한 직원 목록
    public List<HospitalStaff> listStaffOf(String adminId) {
        return hospitalStaffRepository.findByAdmin_MemberId(adminId);
    }

    // 이 직원에게 권한을 부여한 관리자ID (카메라 등 관리자 소유 자원을 직원이 같이 볼 때 사용)
    // grantAccess()에서 한 직원이 동시에 두 병원 소속이 되는 걸 막아뒀으므로 최대 1건만 존재한다
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
