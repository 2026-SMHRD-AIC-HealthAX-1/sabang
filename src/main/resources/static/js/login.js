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

            window.location.href = "index.html";

        } catch (error) {

            message.innerText = "로그인에 실패했습니다. 다시 시도해 주세요.";
        }
    });
