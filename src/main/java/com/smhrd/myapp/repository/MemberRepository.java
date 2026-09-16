package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, String> {

    // existsByEmail/existsByPhone, 단일 결과 findBy는 Hibernate가 결과 건수를 제한하는 쿼리로 바꾸는데
    // 그 과정에서 "FETCH FIRST ? ROWS ONLY" 구문을 쓰게 되고, 캠퍼스 오라클 DB가 이를 지원하지 않아
    // ORA-00933이 발생함. COUNT/List 반환 쿼리는 결과 제한이 붙지 않아 이 문제가 없음.
    long countByEmail(String email);

    long countByPhone(String phone);

    List<Member> findByEmailAndPhone(String email, String phone);

    List<Member> findByMemberIdAndEmailAndPhone(String memberId, String email, String phone);
}
