/*
===========================================
    구독 결제 팝업 (guide.html)

    실제 결제는 하지 않는 데모입니다.
    결제하기를 누르면 서버에 구독 완료 처리를 요청하고
    성공하면 대시보드로 이동합니다.
===========================================
*/

const openBtn = document.getElementById("openPaymentBtn");
const closeBtn = document.getElementById("closePaymentBtn");
const backdrop = document.getElementById("paymentModalBackdrop");
const modal = document.getElementById("paymentModal");
const form = document.getElementById("paymentForm");
const message = document.getElementById("paymentMessage");

function openModal() {
    message.textContent = "";
    modal.hidden = false;
}

function closeModal() {
    modal.hidden = true;
}

openBtn.addEventListener("click", async function() {

    const response = await fetch("/api/auth/me");

    if (!response.ok) {
        alert("로그인 후 이용해 주세요.");
        window.location.href = "login.html";
        return;
    }

    openModal();
});

closeBtn.addEventListener("click", closeModal);
backdrop.addEventListener("click", closeModal);

form.addEventListener("submit", async function(event) {

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
            message.textContent = data.message || "결제에 실패했습니다.";
            return;
        }

        alert("결제가 완료되었습니다.");

        window.location.href = "dashboard.html";

    } catch (error) {

        message.textContent = "결제에 실패했습니다. 다시 시도해 주세요.";
    }
});
