package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.HospitalStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HospitalStaffRepository extends JpaRepository<HospitalStaff, Long> {

    // count 쿼리 사용 이유: MemberRepository의 existsBy 관련 주석 참고
    long countByStaff_MemberId(String memberId);

    long countByAdmin_MemberIdAndStaff_MemberId(String adminId, String staffId);

    List<HospitalStaff> findByAdmin_MemberId(String adminId);

    List<HospitalStaff> findByStaff_MemberId(String staffId);

    // HISTORY_ID가 시퀀스/트리거로 자동 채워지지 않아 저장 전 직접 계산해야 함
    @Query("SELECT COALESCE(MAX(h.historyId), 0) FROM HospitalStaff h")
    Long findMaxId();

    // 권한 회수
    void deleteByAdmin_MemberIdAndStaff_MemberId(String adminId, String staffId);

    // 회원탈퇴 시 정리용: 이 관리자가 부여한 권한 전체 / 이 회원이 부여받은 권한 전체
    void deleteByAdmin_MemberId(String adminId);

    void deleteByStaff_MemberId(String staffId);
}
