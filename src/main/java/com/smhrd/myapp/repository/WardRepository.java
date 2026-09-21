package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {

    // 관리자(=병원)가 등록한 병동 전체
    List<Ward> findByAdmin_MemberId(String adminId);

    // 병동번호 중복확인: 같은 병원 안에서만 유일하면 됨
    // count 쿼리 사용 이유: MemberRepository의 existsBy 관련 주석 참고
    // (existsBy 파생쿼리는 "FETCH FIRST ? ROWS ONLY"를 쓰는데 캠퍼스 오라클 DB가 지원하지 않아 ORA-00933 발생)
    long countByAdmin_MemberIdAndWardCode(String adminId, String wardCode);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 병동 전체 삭제
    void deleteByAdmin_MemberId(String adminId);
}
