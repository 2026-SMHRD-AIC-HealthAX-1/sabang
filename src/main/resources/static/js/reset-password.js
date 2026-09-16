/*
===========================================
    비밀번호 재설정

    find-password.html에서 본인 확인이 끝나야
    세션에 정보가 남아있고, 없으면 다시 확인하게 한다.
===========================================
*/

const resetKey = "framVision.passwordReset.v1";

const verifiedInfo = JSON.parse(
    sessionStorage.getItem(resetKey) || "null"
);

if (!verifiedInfo) {
    alert("본인 확인이 필요합니다. 비밀번호 찾기를 먼저 진행해 주세요.");
    window.location.href = "find-password.html";
}

document
    .getElementById("resetPasswordForm")
    .addEventListener("submit", async function(event) {

        event.preventDefault();

        const newPassword = document.getElementById("newPassword").value;
        const newPasswordConfirm = document.getElementById("newPasswordConfirm").value;

        const message = document.getElementById("message");

        message.innerText = "";

        if (newPassword !== newPasswordConfirm) {
            message.innerText = "비밀번호가 일치하지 않습니다.";
            return;
        }

        try {

            const response = await fetch("/api/auth/reset-password", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    memberId: verifiedInfo.memberId,
                    email: verifiedInfo.email,
                    phone: verifiedInfo.phone,
                    newPassword
                })
            });

            const data = await response.json();

            if (!response.ok) {
                message.innerText = data.message || "비밀번호 변경에 실패했습니다.";
                return;
            }

            sessionStorage.removeItem(resetKey);

            alert("비밀번호가 변경되었습니다. 다시 로그인해 주세요.");

            window.location.href = "login.html";

        } catch (error) {

            message.innerText = "비밀번호 변경에 실패했습니다. 다시 시도해 주세요.";
        }
    });
