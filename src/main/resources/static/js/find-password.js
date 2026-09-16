document
    .getElementById("findPasswordForm")
    .addEventListener("submit", async function(event) {

        event.preventDefault();

        const memberId = document.getElementById("memberId").value.trim();
        const email = document.getElementById("email").value.trim();
        const phone = document.getElementById("phone").value.trim();

        const message = document.getElementById("message");

        message.innerText = "";

        try {

            const response = await fetch("/api/auth/find-password/verify", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ memberId, email, phone })
            });

            const data = await response.json();

            if (!response.ok) {
                message.innerText = data.message || "일치하는 회원 정보가 없습니다.";
                return;
            }

            // 재설정 화면에서 다시 본인 확인할 값을 세션에 잠시 보관
            sessionStorage.setItem(
                "framVision.passwordReset.v1",
                JSON.stringify({ memberId, email, phone })
            );

            window.location.href = "reset-password.html";

        } catch (error) {

            message.innerText = "본인 확인에 실패했습니다. 다시 시도해 주세요.";
        }
    });
