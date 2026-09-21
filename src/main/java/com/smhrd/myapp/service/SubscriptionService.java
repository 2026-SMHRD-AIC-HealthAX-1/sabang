package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.entity.Subscription;
import com.smhrd.myapp.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionService {

    // DB에는 한글 대신 짧은 영문 코드로 저장 (데이터 용량 절약)
    private static final String PAID = "paid";

    // 구독 개월수 -> 상품 코드
    private static final Map<Integer, String> PRODUCT_NAMES = Map.of(
            1, "1month",
            3, "3month",
            6, "6month",
            12, "12month"
    );

    // 상품 코드 -> 결제금액
    // TODO: 시스템 구축 완료 후 요금제(가격) 테이블/컬럼으로 분리 예정. 지금은 임시 하드코딩.
    private static final Map<String, Long> AMOUNTS_BY_PRODUCT = Map.of(
            "1month", 99_000L,
            "3month", 270_000L,
            "6month", 510_000L,
            "12month", 950_000L
    );

    // 화면 표시용 한글 라벨 (DB에는 저장하지 않음, profile.html billing 표시 전용)
    private static final Map<String, String> PAYMENT_METHOD_LABELS = Map.of(
            "credit_card", "신용카드",
            "bank_transfer", "계좌이체",
            "kakao_pay", "카카오페이",
            "virtual_account", "무통장입금"
    );

    private static final Map<String, String> STATUS_LABELS = Map.of(
            PAID, "결제완료"
    );

    private final SubscriptionRepository subscriptionRepository;
    private final MemberService memberService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, MemberService memberService) {
        this.subscriptionRepository = subscriptionRepository;
        this.memberService = memberService;
    }

    // 결제완료(PAYMENT_STATUS='paid')이면서 아직 만료(END_DATE)되지 않은 구독이 있으면 관리자로 인정
    public boolean isActiveAdmin(String memberId) {
        return subscriptionRepository.countByAdmin_MemberIdAndPaymentStatusAndEndDateGreaterThanEqual(
                memberId, PAID, LocalDate.now()) > 0;
    }

    // 결제(데모): 실제 결제 연동 없이 새 구독 레코드만 생성
    // 아직 안 끝난 구독이 남아있으면(만료 전 미리 연장) 그 만료일부터 이어서 개월수를 더한다.
    // 그냥 오늘부터 다시 계산하면 미리 연장한 만큼 손해라서 그렇다 - 이미 만료됐으면 오늘부터 시작.
    public Subscription pay(String memberId, int months, String paymentMethod) {

        String productName = PRODUCT_NAMES.get(months);

        if (productName == null) {
            throw new IllegalArgumentException("지원하지 않는 구독 기간입니다: " + months + "개월");
        }

        Member admin = memberService.findById(memberId);

        if (admin == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        LocalDate today = LocalDate.now();

        LocalDate currentPaidEndDate = subscriptionRepository.findByAdmin_MemberIdOrderByStartDateDescSubscriptionIdDesc(memberId).stream()
                .filter(s -> PAID.equals(s.getPaymentStatus()))
                .map(Subscription::getEndDate)
                .filter(end -> end != null)
                .max(Comparator.naturalOrder())
                .orElse(today);

        LocalDate extendFrom = currentPaidEndDate.isAfter(today) ? currentPaidEndDate : today;

        Subscription subscription = new Subscription();
        subscription.setAdmin(admin);
        subscription.setProductName(productName);
        subscription.setPaymentMethod(paymentMethod);
        subscription.setPaymentStatus(PAID);
        subscription.setStartDate(today);
        subscription.setEndDate(extendFrom.plusMonths(months));

        return subscriptionRepository.save(subscription);
    }

    // profile.html 구독결제 정보(관리자 전용)에 쓰이는 최신 구독 조회
    public Map<String, Object> getBillingInfo(String memberId) {

        List<Subscription> subscriptions = subscriptionRepository.findByAdmin_MemberIdOrderByStartDateDescSubscriptionIdDesc(memberId);

        if (subscriptions.isEmpty()) {
            return null;
        }

        Subscription latest = subscriptions.get(0);

        long amount = AMOUNTS_BY_PRODUCT.getOrDefault(latest.getProductName(), 0L);

        // 코드값을 화면 표시용 한글로 변환. 코드에 없으면(과거 한글로 저장된 테스트 데이터 등) 원본 그대로 표시
        String statusLabel = STATUS_LABELS.getOrDefault(latest.getPaymentStatus(), latest.getPaymentStatus());
        String methodLabel = PAYMENT_METHOD_LABELS.getOrDefault(latest.getPaymentMethod(), latest.getPaymentMethod());

        return Map.of(
                "status", statusLabel,
                "lastPaymentDate", latest.getStartDate(),
                "nextPaymentDate", latest.getEndDate(),
                "paymentMethod", methodLabel,
                "amount", amount
        );
    }

    // 회원탈퇴 시 이 관리자의 구독 이력 정리
    // deleteBy 파생 쿼리는 트랜잭션이 없으면 실패함 (HospitalStaffService 주석 참고)
    @Transactional
    public void deleteAllByAdmin(String adminId) {
        subscriptionRepository.deleteByAdmin_MemberId(adminId);
    }
}
