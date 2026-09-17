package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // count 쿼리 사용 이유: MemberRepository의 existsBy 관련 주석 참고
    // (파생 exists/단일결과 쿼리가 Hibernate에서 FETCH FIRST로 변환되어 캠퍼스 오라클 DB와 호환 안 됨)
    long countByAdmin_MemberIdAndPaymentStatus(String memberId, String paymentStatus);

    List<Subscription> findByAdmin_MemberIdOrderByStartDateDesc(String memberId);

    // SUBSCRIPTION_ID가 시퀀스/트리거로 자동 채워지지 않아 저장 전 직접 계산해야 함
    @Query("SELECT COALESCE(MAX(s.subscriptionId), 0) FROM Subscription s")
    Long findMaxId();

    // 회원탈퇴 시 이 관리자의 구독 이력 정리용
    void deleteByAdmin_MemberId(String adminId);
}
