package com.smhrd.myapp.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "SUBSCRIPTION")
public class Subscription {
	
	// DB : SUBSCRIPTION_ID NUMBER(10) PRIMARY KEY
	@Id
    @Column(name = "SUBSCRIPTION_ID")
    private Long subscriptionId;

	// 관리자 ID
    // SUBSCRIPTION.ADMIN_ID가
    // MEMBER.MEMBER_ID를 참조하는 Foreign Key
    @ManyToOne
    @JoinColumn(
        name = "ADMIN_ID",
        referencedColumnName = "MEMBER_ID"
    )
    private Member admin;

    // 구독 상품명
    // DB : PRODUCT_NAME VARCHAR2(100) NOT NULL
    @Column(name = "PRODUCT_NAME", nullable = false, length = 100)
    private String productName;

    // 결제 방법
    // DB : PAYMENT_METHOD VARCHAR2(30)
    @Column(name = "PAYMENT_METHOD", length = 30)
    private String paymentMethod;

    // 결제 상태
    // DB : PAYMENT_STATUS VARCHAR2(20) NOT NULL
    @Column(name = "PAYMENT_STATUS", nullable = false, length = 20)
    private String paymentStatus;

    // 구독 시작일
    // DB : START_DATE DATE NOT NULL
    @Column(name = "START_DATE", nullable = false)
    private LocalDate startDate;

    // 구독 종료일
    // DB : END_DATE DATE
    @Column(name = "END_DATE")
    private LocalDate endDate;

    // Getter / Setter
    
    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Member getAdmin() {
        return admin;
    }

    public void setAdmin(Member admin) {
        this.admin = admin;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

}
