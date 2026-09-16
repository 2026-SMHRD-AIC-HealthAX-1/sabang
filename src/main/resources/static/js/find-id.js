document
    .getElementById("findIdForm")
    .addEventListener("submit", async function(event) {

        event.preventDefault();

        const email = document.getElementById("email").value.trim();
        const phone = document.getElementById("phone").value.trim();

        const message = document.getElementById("message");
        const result = document.getElementById("result");

        message.innerText = "";
        result.innerText = "";

        try {

            const response = await fetch("/api/auth/find-id", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, phone })
            });

            const data = await response.json();

            if (!response.ok) {
                message.innerText = data.message || "일치하는 회원 정보가 없습니다.";
                return;
            }

            result.innerText = `회원님의 아이디는 "${data.memberId}" 입니다.`;

        } catch (error) {

            message.innerText = "아이디 찾기에 실패했습니다. 다시 시도해 주세요.";
        }
    });
