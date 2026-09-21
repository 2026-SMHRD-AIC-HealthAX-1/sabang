document
    .getElementById("loginForm")
    .addEventListener("submit", async function(event) {

        event.preventDefault();

        const memberId = document.getElementById("memberId").value.trim();
        const password = document.getElementById("password").value;

        const message = document.getElementById("message");

        message.innerText = "";

        if (!memberId || !password) {
            return;
        }

        try {

            const response = await fetch("/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ memberId, password })
            });

            const data = await response.json();

            if (!response.ok) {
                message.innerText = data.message || "로그인에 실패했습니다.";
                return;
            }

            sessionStorage.setItem("framVision.isAdmin", String(!!data.isAdmin));
            sessionStorage.setItem("framVision.memberName", data.memberName);

            // 관리자면 구독결제 정보를 미리 받아서 캐시해둔다.
            // (페이지 이동하면 로그인 화면의 JS는 사라지므로 이동 전에 끝내야 함)
            if (data.isAdmin) {

                try {

                    const billingResponse = await fetch("/api/subscription/me");

                    if (billingResponse.ok) {

                        const billing = await billingResponse.json();

                        sessionStorage.setItem("framVision.billingCache", JSON.stringify(billing));

                        // 구독 만료가 일주일(7일) 이내로 남았으면 로그인 시 미리 알려준다
                        if (billing.nextPaymentDate) {

                            const daysLeft = Math.ceil(
                                (new Date(billing.nextPaymentDate) - new Date()) / (1000 * 60 * 60 * 24)
                            );

                            if (daysLeft >= 0 && daysLeft <= 7) {
                                alert(`구독 만료가 ${daysLeft}일 남았습니다. 만료 전에 연장해 주세요.`);
                            }
                        }
                    }

                } catch (error) {
                    // 미리 받아두기에 실패해도 로그인 자체는 계속 진행
                }
            }

            // 대시보드 접근 권한이 없으면(구독 만료 등) 이용안내 화면으로 보낸다 (거기서 결제/탈퇴 가능)
            window.location.href = data.hasAccess ? "dashboard.html" : "guide.html";

        } catch (error) {

            message.innerText = "로그인에 실패했습니다. 다시 시도해 주세요.";
        }
    });
