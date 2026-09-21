package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.HospitalStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalStaffRepository extends JpaRepository<HospitalStaff, Long> {

    // count 쿼리 사용 이유: MemberRepository의 existsBy 관련 주석 참고
    long countByStaff_MemberId(String memberId);

    long countByAdmin_MemberIdAndStaff_MemberId(String adminId, String staffId);

    List<HospitalStaff> findByAdmin_MemberId(String adminId);

    List<HospitalStaff> findByStaff_MemberId(String staffId);

    // 권한 회수
    void deleteByAdmin_MemberIdAndStaff_MemberId(String adminId, String staffId);

    // 회원탈퇴 시 정리용: 이 관리자가 부여한 권한 전체 / 이 회원이 부여받은 권한 전체
    void deleteByAdmin_MemberId(String adminId);

    void deleteByStaff_MemberId(String staffId);
}
