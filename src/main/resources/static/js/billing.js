/*
===========================================
    구독결제 정보 (관리자 전용)

    profile.js(왼쪽 회원정보, localStorage 기반)와는 별도로
    실제 로그인 세션(/api/auth/me) 기준으로 관리자인 경우에만
    구독결제 정보를 채운다. 기본은 숨김 상태.

    서버 확인을 기다리면 왼쪽 회원정보(로컬 데이터라 즉시 표시)보다
    항상 늦게 떠서 신뢰성이 떨어져 보인다. 그래서 같은 세션 안에서
    이전에 확인한 값이 있으면 그 값을 즉시 먼저 보여주고(왼쪽과 동시에),
    뒤에서 서버에 다시 확인해서 조용히 최신값으로 맞춘다.
    (세션의 첫 방문 때만 서버 응답을 기다려야 해서 어쩔 수 없이 늦게 뜬다)
===========================================
*/

(async () => {
    "use strict";

    const section = document.getElementById("billingSection");
    const isAdminCacheKey = "framVision.isAdmin";
    const billingCacheKey = "framVision.billingCache";

    /*
    ===========================================
        구독 연장 결제 팝업

        실제 결제는 하지 않는 데모입니다. guide.html의 결제 팝업과 같은 흐름이지만,
        여기서는 결제 후 다른 페이지로 보내지 않고 이 화면에서 구독결제 정보만 새로 고친다.
    ===========================================
    */

    const openPaymentBtn = document.getElementById("openPaymentBtn");
    const closePaymentBtn = document.getElementById("closePaymentBtn");
    const paymentBackdrop = document.getElementById("paymentModalBackdrop");
    const paymentModal = document.getElementById("paymentModal");
    const paymentForm = document.getElementById("paymentForm");
    const paymentMessage = document.getElementById("paymentMessage");

    function openPaymentModal() {
        paymentMessage.textContent = "";
        paymentModal.hidden = false;
    }

    function closePaymentModal() {
        paymentModal.hidden = true;
    }

    openPaymentBtn.addEventListener("click", openPaymentModal);
    closePaymentBtn.addEventListener("click", closePaymentModal);
    paymentBackdrop.addEventListener("click", closePaymentModal);

    paymentForm.addEventListener("submit", async function(event) {

        event.preventDefault();

        const months = Number(document.getElementById("paymentMonths").value);
        const paymentMethod = document.getElementById("paymentMethod").value;

        try {

            const response = await fetch("/api/subscription/pay", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ months, paymentMethod })
            });

            const data = await response.json();

            if (!response.ok) {
                paymentMessage.textContent = data.message || "결제에 실패했습니다.";
                return;
            }

            closePaymentModal();

            const billingResponse = await fetch("/api/subscription/me");

            if (billingResponse.ok) {

                const billing = await billingResponse.json();

                sessionStorage.setItem(billingCacheKey, JSON.stringify(billing));

                renderBilling(billing);
            }

            alert("구독이 연장되었습니다.");

        } catch (error) {

            paymentMessage.textContent = "결제에 실패했습니다. 다시 시도해 주세요.";
        }
    });

    function renderBilling(billing) {

        document.getElementById("billingStatus").textContent = billing.status;
        document.getElementById("billingLastPayment").textContent = billing.lastPaymentDate;
        document.getElementById("billingNextPayment").textContent = billing.nextPaymentDate;
        document.getElementById("billingAmount").textContent = billing.amount.toLocaleString() + "원";
        document.getElementById("billingMethod").textContent = billing.paymentMethod;

        const sampleLabel = document.getElementById("billingSampleLabel");
        const note = document.getElementById("billingNote");

        if (sampleLabel) {
            sampleLabel.remove();
        }

        if (note) {
            note.remove();
        }

        section.hidden = false;
    }

    // 캐시된 값이 있으면 서버 응답을 기다리지 않고 바로 보여준다 (왼쪽과 동시에 뜸)
    const cachedBilling = sessionStorage.getItem(billingCacheKey);

    if (sessionStorage.getItem(isAdminCacheKey) === "true" && cachedBilling) {

        try {
            renderBilling(JSON.parse(cachedBilling));
        } catch (error) {
            // 캐시가 깨졌으면 무시하고 아래에서 새로 조회한 값으로 채운다
        }
    }

    try {

        const meResponse = await fetch("/api/auth/me");

        if (!meResponse.ok) {
            section.hidden = true;
            sessionStorage.removeItem(billingCacheKey);
            return;
        }

        const me = await meResponse.json();

        sessionStorage.setItem(isAdminCacheKey, String(!!me.isAdmin));

        if (!me.isAdmin) {
            section.hidden = true;
            sessionStorage.removeItem(billingCacheKey);
            return;
        }

        const billingResponse = await fetch("/api/subscription/me");

        if (!billingResponse.ok) {
            section.hidden = true;
            sessionStorage.removeItem(billingCacheKey);
            return;
        }

        const billing = await billingResponse.json();

        sessionStorage.setItem(billingCacheKey, JSON.stringify(billing));

        renderBilling(billing);

    } catch (error) {
        // 캐시로 이미 보여주고 있었다면 그대로 두고, 없었다면 숨긴 채로 둔다
    }
})();
