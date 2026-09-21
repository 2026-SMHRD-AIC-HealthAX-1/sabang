package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // count 쿼리 사용 이유: MemberRepository의 existsBy 관련 주석 참고
    // (파생 exists/단일결과 쿼리가 Hibernate에서 FETCH FIRST로 변환되어 캠퍼스 오라클 DB와 호환 안 됨)
    long countByAdmin_MemberIdAndPaymentStatus(String memberId, String paymentStatus);

    // 관리자 자격 확인용: 결제완료 + 아직 만료(END_DATE)되지 않은 구독이 있는지
    long countByAdmin_MemberIdAndPaymentStatusAndEndDateGreaterThanEqual(
            String memberId, String paymentStatus, LocalDate today);

    // START_DATE만으로 정렬하면 같은 날 두 번 결제(연장)했을 때 동점이라 순서가 안 보장됨.
    // SUBSCRIPTION_ID(생성 순서, 계속 증가)를 2차 정렬 기준으로 둬서 진짜 최신 결제를 확실히 맨 앞에 오게 한다.
    List<Subscription> findByAdmin_MemberIdOrderByStartDateDescSubscriptionIdDesc(String memberId);

    // 회원탈퇴 시 이 관리자의 구독 이력 정리용
    void deleteByAdmin_MemberId(String adminId);
}
