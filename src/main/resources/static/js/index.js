/*
===========================================
    로그인 상태에 따라 헤더 버튼 전환

    로그인 O: 로그인 -> 로그아웃 / 회원가입 -> 이용안내
    로그인 X: 기존 그대로 (로그인 / 회원가입)
===========================================
*/

const loginBtn = document.getElementById("authLoginBtn");
const signupBtn = document.getElementById("authSignupBtn");

async function applyAuthState() {

    try {

        const response = await fetch("/api/auth/me");

        if (!response.ok) {
            return;
        }

        loginBtn.textContent = "로그아웃";
        loginBtn.removeAttribute("href");
        loginBtn.style.cursor = "pointer";

        loginBtn.addEventListener("click", async function() {

            await fetch("/api/auth/logout", { method: "POST" });

            window.location.href = "index.html";
        });

        signupBtn.textContent = "이용안내";
        signupBtn.href = "guide.html";

    } catch (error) {
        // 로그인 상태 확인 실패 시 기본(로그아웃 상태) 화면 유지
    }
}

applyAuthState();
