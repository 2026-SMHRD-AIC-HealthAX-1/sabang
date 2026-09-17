/*
===========================================
    구독결제 정보 (관리자 전용)

    profile.js(왼쪽 회원정보, localStorage 기반)와는 별도로
    실제 로그인 세션(/api/auth/me) 기준으로 관리자인 경우에만
    구독결제 정보를 채운다. 기본은 숨김 상태.
===========================================
*/

(async () => {
    "use strict";

    const section = document.getElementById("billingSection");

    try {

        const meResponse = await fetch("/api/auth/me");

        if (!meResponse.ok) {
            return;
        }

        const me = await meResponse.json();

        if (!me.isAdmin) {
            return;
        }

        const billingResponse = await fetch("/api/subscription/me");

        if (!billingResponse.ok) {
            return;
        }

        const billing = await billingResponse.json();

        document.getElementById("billingStatus").textContent = billing.status;
        document.getElementById("billingLastPayment").textContent = billing.lastPaymentDate;
        document.getElementById("billingNextPayment").textContent = billing.nextPaymentDate;
        document.getElementById("billingAmount").textContent = billing.amount.toLocaleString() + "원";
        document.getElementById("billingMethod").textContent = billing.paymentMethod;

        document.getElementById("billingSampleLabel").remove();
        document.getElementById("billingNote").remove();

        section.hidden = false;

    } catch (error) {
        // 조회 실패 시 섹션을 숨긴 상태로 유지
    }
})();
